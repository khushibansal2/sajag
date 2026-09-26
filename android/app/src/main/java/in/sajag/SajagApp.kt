package `in`.sajag

import android.app.Application
import androidx.room.Room
import `in`.sajag.data.SajagDb

class SajagApp : Application() {
    lateinit var db: SajagDb
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, SajagDb::class.java, "sajag.db").build()
    }
}
