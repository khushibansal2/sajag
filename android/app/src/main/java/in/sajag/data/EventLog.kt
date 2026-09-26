package `in`.sajag.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * The attempt event log — append-only, device-signed, ULID-keyed.
 *
 * Append-only is not a style preference; it is what makes sync conflict-free.
 * Two devices can never disagree about an attempt because neither one ever
 * edits an attempt. That property is what lets a supervisor's phone act as a
 * courier for a crew that has been 200 m underground all shift, and it is why
 * there is no merge logic anywhere in this app.
 *
 * The log is also the audit trail. It reconstructs the whole attempt years
 * later in a dispute, which is what turns a certificate from a claim into an
 * evidence file. So: no UPDATE, no DELETE, ever. There is deliberately no DAO
 * method for either.
 */

@Entity(tableName = "attempt")
data class AttemptEntity(
    @PrimaryKey val attemptId: String,        // ULID
    val workerId: String,
    val moduleId: String,                     // "FIRE-01"
    val scenarioVersion: String,
    val tierCeiling: Int,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val supervisorSig: String?,               // co-signature, anti-proxy (D-03)
    val synced: Boolean = false,
)

@Entity(
    tableName = "attempt_event",
    foreignKeys = [ForeignKey(
        entity = AttemptEntity::class,
        parentColumns = ["attemptId"],
        childColumns = ["attemptId"],
        onDelete = ForeignKey.RESTRICT,       // events outlive nothing
    )],
    indices = [Index("attemptId"), Index("synced")],
)
data class EventEntity(
    @PrimaryKey val eventId: String,          // ULID — sortable, collision-free offline
    val attemptId: String,
    val seq: Int,
    val atMs: Long,
    val beat: String,                         // "1.2"
    val itemId: String?,                      // rubric item, null for pure markers
    val type: String,                         // "ITEM" | "WATER_ON_ELECTRICAL" | ...
    val tier: Int,                            // the tier ACTUALLY used for this event
    /** Rubric-specific fields as canonical JSON. Kept opaque on purpose: the
     *  scoring engine owns their meaning, the log just records them. */
    val payloadJson: String,
    val synced: Boolean = false,
)

@Dao
interface AttemptDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun startAttempt(attempt: AttemptEntity)

    /** Idempotent by construction: the ULID is generated once, on the device
     *  that produced the event, so a retried push is a no-op server-side. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun append(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun appendAll(events: List<EventEntity>)

    @Query("SELECT * FROM attempt_event WHERE attemptId = :attemptId ORDER BY seq ASC")
    suspend fun eventsFor(attemptId: String): List<EventEntity>

    @Query("SELECT * FROM attempt_event WHERE synced = 0 ORDER BY atMs ASC LIMIT :limit")
    suspend fun unsynced(limit: Int = 500): List<EventEntity>

    /** The only mutation allowed, and it touches no attempt data — just the
     *  bookkeeping flag that says a copy reached the server. */
    @Query("UPDATE attempt_event SET synced = 1 WHERE eventId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT * FROM attempt WHERE attemptId = :attemptId")
    suspend fun attempt(attemptId: String): AttemptEntity?

    @Query("SELECT * FROM attempt WHERE workerId = :workerId ORDER BY startedAtMs DESC")
    fun attemptsFor(workerId: String): Flow<List<AttemptEntity>>

    @Query("SELECT COUNT(*) FROM attempt_event WHERE synced = 0")
    fun unsyncedCount(): Flow<Int>

    /** Every attempt taken on this phone, newest first: the training centre view. */
    @Query("SELECT * FROM attempt ORDER BY startedAtMs DESC")
    fun allAttempts(): Flow<List<AttemptEntity>>

    /** Every event on this phone, grouped by attempt, for re-scoring on the phone. */
    @Query("SELECT * FROM attempt_event ORDER BY attemptId ASC, seq ASC")
    suspend fun allEvents(): List<EventEntity>
}

@Entity(tableName = "credential")
data class CredentialEntity(
    @PrimaryKey val credentialId: String,     // hex attempt digest
    val attemptId: String,
    val qrText: String,
    val provisional: Boolean,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
)

@Dao
interface CredentialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(credential: CredentialEntity)

    /** A final, issuer-signed credential replaces the provisional one for the
     *  same attempt. That is the only case where a row is overwritten, and it
     *  is keyed on the attempt digest so it cannot replace anything else. */
    @Query("SELECT * FROM credential WHERE attemptId = :attemptId ORDER BY provisional ASC LIMIT 1")
    suspend fun bestFor(attemptId: String): CredentialEntity?

    @Query("SELECT * FROM credential ORDER BY issuedAtMs DESC")
    fun wallet(): Flow<List<CredentialEntity>>
}

@Database(
    entities = [AttemptEntity::class, EventEntity::class, CredentialEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SajagDb : RoomDatabase() {
    abstract fun attempts(): AttemptDao
    abstract fun credentials(): CredentialDao
}

/**
 * Canonical serialisation for the credential digest.
 *
 * MUST produce byte-identical output to `scoring.canonical_event_log` in the
 * Python core, or a credential minted on the server will not match the attempt
 * the device recorded. There is a golden-vector test for exactly this in
 * core/tests/ — port it here before you trust this function.
 */
object CanonicalLog {
    fun bytes(events: List<EventEntity>): ByteArray {
        val body = events.sortedBy { it.seq }.joinToString(",") { e ->
            // keys sorted, no whitespace, floats rounded to 4 dp — see the
            // Python implementation, which is the reference.
            """{"beat":"${e.beat}","item":${e.itemId?.let { "\"$it\"" } ?: "null"}""" +
                ""","payload":${e.payloadJson},"type":"${e.type}"}"""
        }
        return "[$body]".toByteArray(Charsets.UTF_8)
    }
}
