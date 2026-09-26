package `in`.sajag.i18n

/**
 * App chrome in English and Hindi; Santali comes from SantaliText.kt. Module content lives in assess/ModuleContent.kt.
 *
 * TODO(native speaker): every Hindi line needs a pass by a native speaker from
 * the mining belt before a pilot, and every Santali line by a native Santali speaker.
 */
object S {
    // ------------------------------------------------------------ general
    val tagline = T("Safety training you practise, not just read.", "सुरक्षा प्रशिक्षण जिसे आप करके सीखते हैं, सिर्फ पढ़कर नहीं।")
    val language = T("Language", "भाषा")
    val chooseLanguage = T("Choose your language", "अपनी भाषा चुनें")
    val santaliNotice = T(
        "Instructions are shown in Santali. If your phone has no Santali voice, they are spoken in Hindi.",
        "निर्देश संथाली में दिखाए जाते हैं। अगर आपके फ़ोन में संथाली आवाज़ नहीं है, तो वे हिन्दी में बोले जाएंगे।",
        "ᱦᱩᱠᱩᱢ ᱠᱚ ᱥᱟᱱᱛᱟᱲᱤ ᱛᱮ ᱧᱮᱞᱚᱜᱼᱟ᱾ ᱟᱢᱟᱜ ᱯᱷᱚᱱ ᱨᱮ ᱥᱟᱱᱛᱟᱲᱤ ᱟᱲᱟᱝ ᱵᱟᱱᱩᱜ ᱠᱷᱟᱱ, ᱦᱤᱱᱫᱤ ᱛᱮ ᱨᱚᱲᱚᱜᱼᱟ᱾",
    )
    val back = T("Back", "वापस")
    val next = T("Next", "आगे")
    val save = T("Save", "सहेजें")
    val saved = T("Saved", "सहेजा गया")
    val cancel = T("Cancel", "रद्द करें")
    val confirm = T("Confirm", "पक्का करें")
    val edit = T("Edit", "बदलें")
    val remove = T("Remove", "हटाएँ")
    val check = T("Check", "जाँचें")
    val done = T("Done", "हो गया")
    val undo = T("Undo", "वापस")
    val working = T("Please wait", "कृपया रुकें")
    val home = T("Home", "होम")
    val settings = T("Settings", "सेटिंग्स")
    val optional = T("Optional", "वैकल्पिक")
    val notSet = T("Not set", "सेट नहीं है")
    val active = T("Active", "अभी चालू")
    val minutes = T("min", "मिनट")
    val steps = T("steps", "चरण")
    val step = T("Step", "चरण")

    // ------------------------------------------------------------ navigation
    val tabHome = T("Home", "होम")
    val tabPassport = T("Passport", "पासपोर्ट")
    val tabReport = T("Report", "रिपोर्ट")
    val tabVerify = T("Verify", "जाँच")

    // ------------------------------------------------------------ onboarding
    val welcome = T("Welcome to Sajag", "सजग में आपका स्वागत है")
    val aboutYou = T("About you", "आपके बारे में")
    val aboutYouBody = T(
        "Your name and employer code go on your safety certificate.",
        "आपका नाम और नियोक्ता कोड आपके सुरक्षा प्रमाणपत्र पर छपेंगे।",
    )
    val yourName = T("Your name (in English letters)", "आपका नाम (अंग्रेज़ी अक्षरों में)")
    val employerCode = T("Employer / contractor code", "नियोक्ता / ठेकेदार कोड")
    val employerHelp = T("As written on your gate pass, for example CTR-2291", "जैसा आपके गेट पास पर लिखा है, जैसे CTR-2291")
    val district = T("District", "ज़िला")
    val chooseDistrict = T("Choose your district", "अपना ज़िला चुनें")
    val siteName = T("Mine, plant or training centre (optional)", "खदान, प्लांट या प्रशिक्षण केंद्र (वैकल्पिक)")
    val cameraTitle = T("See hazards around you", "अपने आसपास खतरे देखें")
    val cameraBody = T(
        "Drills show fire, smoke and gas on the floor or table in front of you, through the phone's camera.",
        "ड्रिल फ़ोन के कैमरे से आपके सामने के फ़र्श या मेज़ पर आग, धुआँ और गैस दिखाती है।",
    )
    val cameraPrivacy = T(
        "The camera picture stays on this phone. Nothing is recorded or sent.",
        "कैमरे की तस्वीर इसी फ़ोन पर रहती है। कुछ भी रिकॉर्ड या भेजा नहीं जाता।",
    )
    val allowCamera = T("Allow the camera", "कैमरे की अनुमति दें")
    val notNow = T("Not now", "अभी नहीं")
    val startUsing = T("Start", "शुरू करें")

    // ------------------------------------------------------------ home
    val namaste = T("Namaste", "नमस्ते")
    val switchWorker = T("Switch", "बदलें")
    val modulesCertified = T("modules certified", "मॉड्यूल प्रमाणित")
    val emergency = T("Emergency", "आपातकाल")
    val emergencyCardBody = T("Steps, torch and control room. Not an alarm.", "कदम, टॉर्च और कंट्रोल रूम। यह अलार्म नहीं है।")
    val modules = T("Training modules", "प्रशिक्षण मॉड्यूल")
    val comingSoon = T("Coming soon", "जल्द आ रहा है")
    val supervisorTools = T("Supervisor tools", "सुपरवाइज़र के साधन")
    val trainingCentre = T("Training centre", "प्रशिक्षण केंद्र")
    val riskMap = T("Risk map", "जोखिम नक्शा")
    val signOffTool = T("Sign-off", "पुष्टि")
    val demo = T("Demo", "डेमो")
    val pendingSync = T("records waiting to sync", "रिकॉर्ड सिंक होने बाकी")
    val allSynced = T("All records synced", "सभी रिकॉर्ड सिंक हो गए")

    // ------------------------------------------------------------ modes (how a drill is shown)
    val modeAr = T("AR", "AR")
    val modeCamera = T("Camera", "कैमरा")
    val modeGuided = T("Guided", "निर्देशित")
    val trainedIn = T("Trained in", "प्रशिक्षण")
    val guidedWarning = T("Trained in guided 2D mode (no camera).", "निर्देशित 2D मोड में प्रशिक्षण (बिना कैमरे के)।")

    // ------------------------------------------------------------ before the drill
    val briefingTitle = T("Before you start", "शुरू करने से पहले")
    val howItLooks = T("This drill", "यह ड्रिल")
    val briefAr = T(
        "Point the camera at the floor or a table and tap it. The fire, smoke or gas appears there and stays in place while you move around it.",
        "कैमरे को फ़र्श या मेज़ की ओर करें और उस पर टैप करें। आग, धुआँ या गैस वहीं दिखेगी और आपके घूमने पर भी अपनी जगह रहेगी।",
    )
    val briefCamera = T(
        "Hazards are drawn over your camera picture. If the camera is off, a drawn mine gallery is used instead.",
        "खतरे आपके कैमरे की तस्वीर पर बनाए जाते हैं। कैमरा बंद हो तो खदान की बनी हुई गैलरी दिखेगी।",
    )
    val safeAreaTitle = T("Check your surroundings", "अपने आसपास देखें")
    val safeAreaBody = T(
        "Stand in a clear area, away from vehicles, moving machinery, edges and open holes. Do not walk while you look at the screen.",
        "खुली जगह पर खड़े हों, वाहनों, चलती मशीनों, किनारों और खुले गड्ढों से दूर। स्क्रीन देखते हुए न चलें।",
    )
    val safeAreaConfirm = T("I am in a clear, safe area", "मैं खुली और सुरक्षित जगह पर हूँ")
    val supervisorTitle = T("Supervisor sign-off", "सुपरवाइज़र की पुष्टि")
    val supervisorBody = T(
        "Your supervisor confirms that you are the person taking this drill. Without this, the training centre cannot issue your final certificate.",
        "सुपरवाइज़र पुष्टि करते हैं कि यह ड्रिल आप ही दे रहे हैं। इसके बिना प्रशिक्षण केंद्र आपका अंतिम प्रमाणपत्र जारी नहीं कर सकता।",
    )
    val supervisorPin = T("Supervisor PIN", "सुपरवाइज़र पिन")
    val signOff = T("Sign off", "पुष्टि करें")
    val wrongPin = T("Wrong PIN. Try again.", "गलत पिन। फिर से कोशिश करें।")
    val signedBy = T("Signed off by", "पुष्टि करने वाले")
    val noSupervisorSetUp = T(
        "No supervisor is set up on this phone. You can still practise, but the certificate will stay provisional.",
        "इस फ़ोन पर कोई सुपरवाइज़र सेट नहीं है। आप अभ्यास कर सकते हैं, पर प्रमाणपत्र अस्थायी रहेगा।",
    )
    val practiseWithout = T("Practise without sign-off", "बिना पुष्टि के अभ्यास करें")
    val startDrill = T("Start the drill", "ड्रिल शुरू करें")
    val demoPinHint = T("Demo supervisor PIN: 1234", "डेमो सुपरवाइज़र पिन: 1234")

    // ------------------------------------------------------------ drill
    val drillBadge = T("DRILL", "अभ्यास")
    val drillNotAlarm = T("Practice drill. Not a real alarm.", "अभ्यास ड्रिल। यह असली अलार्म नहीं है।")
    val listen = T("Listen", "सुनें")
    val begin = T("I'm ready, begin", "मैं तैयार हूँ, शुरू करें")
    val sequenceHint = T("Tap the steps in the order you would do them.", "कदमों को उसी क्रम में दबाएँ जिसमें आप उन्हें करेंगे।")
    val stopTitle = T("STOP", "रुकिए")
    val stopBody = T("In a real mine, this action can kill. You will repeat this step before you can be certified.", "असली खदान में यह कदम जान ले सकता है। प्रमाणपत्र से पहले आपको यह चरण दोबारा करना होगा।")
    val understood = T("I understand", "मैं समझ गया")
    val cameraOff = T("Camera is off, so a drawn gallery is shown. Tap to allow the camera.", "कैमरा बंद है, इसलिए बनी हुई गैलरी दिख रही है। कैमरा चालू करने के लिए दबाएँ।")
    val leaveTitle = T("Leave this drill?", "क्या यह ड्रिल छोड़नी है?")
    val leaveBody = T("Your answers so far will not count.", "अब तक के आपके जवाब नहीं गिने जाएँगे।")
    val leave = T("Leave", "छोड़ें")
    val stay = T("Stay", "रुकें")
    val hideSheet = T("Hide instructions", "निर्देश छिपाएँ")
    val showSheet = T("Show instructions", "निर्देश दिखाएँ")
    val entryPermit = T("ENTRY PERMIT", "प्रवेश परमिट")
    val arSearching = T(
        "Point the camera at the floor or a table and move the phone slowly.",
        "कैमरे को फ़र्श या मेज़ की ओर करें और फ़ोन को धीरे-धीरे हिलाएँ।",
    )
    val arTapToPlace = T("Tap the floor or table to place the drill there.", "ड्रिल रखने के लिए फ़र्श या मेज़ पर टैप करें।")
    val arAutoPlaced = T("Placed where you are pointing. Tap another spot to move it.", "जहाँ आप इशारा कर रहे हैं वहाँ रखा गया। हटाने के लिए दूसरी जगह टैप करें।")
    val arTooDark = T("Too dark to track. Turn on more light or use your cap lamp.", "ट्रैक करने के लिए बहुत अँधेरा है। रोशनी बढ़ाएँ या कैप लैंप जलाएँ।")
    val arTooFast = T("Move the phone more slowly.", "फ़ोन को और धीरे हिलाएँ।")
    val arNoTexture = T("Point at a surface with some pattern, not a plain wall.", "सादी दीवार नहीं, किसी बनावट वाली सतह की ओर करें।")
    val arCameraBusy = T("The camera is busy. Wait a moment.", "कैमरा व्यस्त है। थोड़ा रुकें।")
    val arUseCamera = T("Use the camera view instead", "इसके बजाय कैमरा दृश्य इस्तेमाल करें")
    val arFellBack = T("AR could not start on this phone. Using the camera view.", "इस फ़ोन पर AR शुरू नहीं हो सका। कैमरा दृश्य इस्तेमाल हो रहा है।")

    // ------------------------------------------------------------ result
    val resultTitle = T("Your result", "आपका परिणाम")
    val passed = T("Passed", "उत्तीर्ण")
    val failed = T("Not yet certified", "अभी प्रमाणित नहीं")
    val failedShort = T("Not yet", "अभी नहीं")
    val score = T("Score", "अंक")
    val floor = T("minimum", "न्यूनतम")
    val reasons = T("What to improve", "क्या सुधारें")
    val replaySteps = T("Steps to repeat", "दोहराने वाले चरण")
    val getCertificate = T("Get my certificate", "मेरा प्रमाणपत्र लें")
    val replay = T("Practise again", "फिर से अभ्यास करें")
    val details = T("How each action was measured", "हर कदम कैसे मापा गया")
    val coSigned = T("Supervisor signed off on this attempt.", "सुपरवाइज़र ने इस प्रयास की पुष्टि की।")
    val notCoSigned = T("No supervisor sign-off. This certificate stays provisional.", "सुपरवाइज़र की पुष्टि नहीं। यह प्रमाणपत्र अस्थायी रहेगा।")
    val signedOff = T("Signed off", "पुष्टि हुई")

    // ------------------------------------------------------------ certificate and verify
    val certificate = T("Safety certificate", "सुरक्षा प्रमाणपत्र")
    val certificateQr = T("Certificate QR code", "प्रमाणपत्र QR कोड")
    val issuedTo = T("This certifies that", "प्रमाणित किया जाता है कि")
    val expires = T("Valid until", "मान्य तिथि तक")
    val verifiedOffline = T("Signature verified offline", "हस्ताक्षर ऑफ़लाइन सत्यापित")
    val provisional = T("Provisional: issued offline on this phone. Becomes final when the training centre confirms it.", "अस्थायी: इस फ़ोन पर ऑफ़लाइन जारी। प्रशिक्षण केंद्र की पुष्टि के बाद अंतिम होगा।")
    val provisionalShort = T("Provisional", "अस्थायी")
    val showToInspector = T("Show this code to your supervisor or DGMS inspector. It can be checked without internet.", "यह कोड अपने सुपरवाइज़र या DGMS निरीक्षक को दिखाएँ। इसे बिना इंटरनेट के जाँचा जा सकता है।")
    val verifyCertificate = T("Verify a certificate", "प्रमाणपत्र जाँचें")
    val verifySubtitle = T("Works offline", "बिना इंटरनेट के काम करता है")
    val scanQr = T("Scan QR code", "QR कोड स्कैन करें")
    val orPaste = T("Or paste the code text", "या कोड का टेक्स्ट चिपकाएँ")
    val valid = T("VALID", "मान्य")
    val rejected = T("NOT VALID", "अमान्य")
    val verifyOfflineNote = T(
        "The check runs on this phone against the training centre's key. No internet, no lookup.",
        "जाँच इसी फ़ोन पर प्रशिक्षण केंद्र की कुंजी से होती है। न इंटरनेट, न कोई खोज।",
    )
    val reasonNotSajag = T("This is not a Sajag certificate.", "यह सजग प्रमाणपत्र नहीं है।")
    val reasonDamaged = T("The code is damaged or incomplete.", "कोड खराब या अधूरा है।")
    val reasonUnknownIssuer = T("Issued by someone this phone does not trust.", "इसे ऐसे व्यक्ति ने जारी किया जिस पर यह फ़ोन भरोसा नहीं करता।")
    val reasonAltered = T("The certificate has been changed. It is not genuine.", "प्रमाणपत्र बदला गया है। यह असली नहीं है।")
    val reasonRevoked = T("This certificate has been cancelled.", "यह प्रमाणपत्र रद्द कर दिया गया है।")
    val reasonNotYet = T("This certificate is not valid yet.", "यह प्रमाणपत्र अभी मान्य नहीं है।")
    val reasonExpired = T("This certificate has expired.", "इस प्रमाणपत्र की अवधि खत्म हो गई है।")

    // ------------------------------------------------------------ passport and worker ID
    val passportTitle = T("Safety passport", "सुरक्षा पासपोर्ट")
    val worker = T("Worker", "कर्मचारी")
    val workerId = T("Worker ID", "कर्मचारी आईडी")
    val workerIdCard = T("Worker safety ID", "कर्मचारी सुरक्षा आईडी")
    val showQr = T("Show QR", "QR दिखाएँ")
    val hideQr = T("Hide QR", "QR छिपाएँ")
    val idCardNotCertificate = T(
        "This is a worker ID card. It says who the worker is; it is not a certificate. Ask for the certificate QR.",
        "यह कर्मचारी आईडी कार्ड है। यह बताता है कि कर्मचारी कौन है; यह प्रमाणपत्र नहीं है। प्रमाणपत्र का QR माँगें।",
    )
    val unknownWorker = T("Worker not on this phone", "यह कर्मचारी इस फ़ोन पर नहीं है")
    val certificatesOnPhone = T("Certificates on this phone", "इस फ़ोन पर प्रमाणपत्र")
    val notTrained = T("Not trained yet", "अभी प्रशिक्षण नहीं")
    val certified = T("Certified", "प्रमाणित")
    val renewSoon = T("Renew soon", "जल्द नवीनीकरण करें")
    val expired = T("Expired", "समाप्त")
    val daysLeft = T("days left", "दिन बाकी")
    val skills = T("Skill scores", "कौशल अंक")
    val skillsHelp = T("Best score from your valid certificates.", "आपके मान्य प्रमाणपत्रों में से सबसे अच्छे अंक।")
    val practiseNext = T("Practise next", "आगे यह अभ्यास करें")
    val weakestSkill = T("Your lowest skill", "आपका सबसे कम कौशल")
    val practiseNow = T("Practise now", "अभी अभ्यास करें")
    val allCurrent = T("All your modules are current.", "आपके सभी मॉड्यूल मान्य हैं।")
    val myCertificates = T("My certificates", "मेरे प्रमाणपत्र")
    val noCertificatesTitle = T("No certificates yet", "अभी कोई प्रमाणपत्र नहीं")
    val noCertificates = T("Pass a module to earn one. It works offline and lives on this phone.", "एक मॉड्यूल पास करके प्रमाणपत्र पाएँ। यह ऑफ़लाइन काम करता है और इसी फ़ोन पर रहता है।")

    // ------------------------------------------------------------ hazard report
    val reportTitle = T("Report a hazard", "खतरे की रिपोर्ट करें")
    val reportSubtitle = T("Saved on the phone, sent when there is network", "फ़ोन पर सहेजी जाती है, नेटवर्क मिलने पर भेजी जाती है")
    val reportWhat = T("What did you see?", "आपने क्या देखा?")
    val hazardFire = T("Fire or smoke", "आग या धुआँ")
    val hazardGas = T("Gas or bad smell", "गैस या बदबू")
    val hazardElectrical = T("Electrical", "बिजली")
    val hazardFall = T("Roof or wall falling", "छत या दीवार गिरना")
    val hazardMachine = T("Machinery", "मशीनरी")
    val hazardSlip = T("Slip or trip", "फिसलना या ठोकर")
    val hazardOther = T("Other", "अन्य")
    val reportWhere = T("Where? (place, level, landmark)", "कहाँ? (जगह, स्तर, पहचान)")
    val reportSeverity = T("How serious?", "कितना गंभीर?")
    val severityLow = T("Low", "कम")
    val severityMedium = T("Medium", "मध्यम")
    val severityHigh = T("High", "अधिक")
    val reportNote = T("What happened? (optional)", "क्या हुआ? (वैकल्पिक)")
    val send = T("Send report", "रिपोर्ट भेजें")
    val pickTypeFirst = T("Pick what you saw first.", "पहले चुनें कि आपने क्या देखा।")
    val confirmSendTitle = T("Send this report?", "यह रिपोर्ट भेजें?")
    val confirmSendBody = T(
        "It is saved on this phone now and goes to the training centre when there is network.",
        "यह अभी इस फ़ोन पर सहेजी जाएगी और नेटवर्क मिलने पर प्रशिक्षण केंद्र को जाएगी।",
    )
    val reportSaved = T("Report saved. It will be sent when there is network.", "रिपोर्ट सहेजी गई। नेटवर्क मिलने पर भेजी जाएगी।")
    val myReports = T("My reports", "मेरी रिपोर्ट")
    val statusWaiting = T("Waiting", "इंतज़ार")
    val statusSent = T("Sent", "भेजी गई")

    // ------------------------------------------------------------ emergency
    val emergencySubtitle = T("What to do, right now", "अभी क्या करें")
    val notAnAlarm = T("This app is not an alarm", "यह ऐप अलार्म नहीं है")
    val emergencyNotAlarm = T(
        "Raise the site alarm first. Follow your supervisor and the mine's emergency plan.",
        "पहले साइट का अलार्म बजाएँ। अपने सुपरवाइज़र और खदान की आपात योजना का पालन करें।",
    )
    val callControlRoom = T("Call control room", "कंट्रोल रूम को फ़ोन करें")
    val call112 = T("Call 112 (national emergency)", "112 पर फ़ोन करें (राष्ट्रीय आपातकाल)")
    val noControlRoom = T(
        "No control room number on this phone. A supervisor can add it in Settings.",
        "इस फ़ोन पर कंट्रोल रूम का नंबर नहीं है। सुपरवाइज़र इसे सेटिंग्स में जोड़ सकते हैं।",
    )
    val noDialler = T("This phone cannot make calls.", "यह फ़ोन कॉल नहीं कर सकता।")
    val torchOn = T("Turn the torch on", "टॉर्च जलाएँ")
    val torchOff = T("Turn the torch off", "टॉर्च बुझाएँ")
    val torchFailed = T("The torch is not available right now.", "टॉर्च अभी उपलब्ध नहीं है।")
    val assemblyPoint = T("Assembly point", "असेंबली पॉइंट")
    val assemblyPointHint = T("Where everyone gathers, e.g. Main gate, north yard", "जहाँ सब इकट्ठा होते हैं, जैसे मुख्य गेट, उत्तर यार्ड")
    val whatToDo = T("What to do", "क्या करें")
    val readAloud = T("Read aloud", "पढ़कर सुनाएँ")
    val stopReading = T("Stop", "रोकें")
    val reportThis = T("Report this hazard", "इस खतरे की रिपोर्ट करें")

    // ------------------------------------------------------------ settings
    val workersOnPhone = T("Workers on this phone", "इस फ़ोन पर कर्मचारी")
    val workersSubtitle = T("One phone can train a whole batch", "एक फ़ोन पूरे बैच को प्रशिक्षण दे सकता है")
    val workersNote = T(
        "Each worker has their own ID and certificates. Tap a name before their drill.",
        "हर कर्मचारी की अपनी आईडी और प्रमाणपत्र होते हैं। उनकी ड्रिल से पहले नाम पर टैप करें।",
    )
    val addWorker = T("Add a worker", "कर्मचारी जोड़ें")
    val editWorker = T("Edit worker", "कर्मचारी बदलें")
    val nowTraining = T("Now training", "अभी प्रशिक्षण में")
    val supervisorSettings = T("Supervisor settings", "सुपरवाइज़र सेटिंग्स")
    val controlRoomNumber = T("Control room number", "कंट्रोल रूम नंबर")
    val phoneNumber = T("Phone number", "फ़ोन नंबर")
    val pinToChange = T("Enter the supervisor PIN to change this.", "इसे बदलने के लिए सुपरवाइज़र पिन डालें।")
    val supervisorLockNote = T(
        "The control room number and assembly point need the supervisor PIN to change.",
        "कंट्रोल रूम नंबर और असेंबली पॉइंट बदलने के लिए सुपरवाइज़र पिन चाहिए।",
    )
    val supervisorUnlockedNote = T(
        "Set up a supervisor to lock the control room number and assembly point with a PIN.",
        "कंट्रोल रूम नंबर और असेंबली पॉइंट को पिन से बंद करने के लिए सुपरवाइज़र सेट करें।",
    )
    val drills = T("Drills", "ड्रिल")
    val useAr = T("Use AR when the phone supports it", "फ़ोन में सुविधा हो तो AR इस्तेमाल करें")
    val useArHelp = T("Hazards stand on the real floor or table. Off: drawn over the camera.", "खतरे असली फ़र्श या मेज़ पर दिखते हैं। बंद: कैमरे पर बने हुए।")
    val arReady = T("AR works on this phone", "इस फ़ोन पर AR काम करता है")
    val arNeedsInstall = T("Google Play Services for AR is needed", "Google Play Services for AR चाहिए")
    val arChecking = T("Checking AR support", "AR सुविधा जाँची जा रही है")
    val arLowSpec = T("AR is installed, but this phone is too slow for it", "AR इंस्टॉल है, पर यह फ़ोन इसके लिए धीमा है")
    val arUnsupported = T("This phone does not support AR. Drills use the camera view.", "यह फ़ोन AR नहीं चलाता। ड्रिल कैमरा दृश्य में चलेगी।")
    val installAr = T("Install AR support", "AR सुविधा इंस्टॉल करें")
    val installArFailed = T("Could not open the Play Store.", "Play Store नहीं खुल सका।")
    val demoMode = T("Demo mode", "डेमो मोड")
    val demoModeHelp = T(
        "Shows sample data on the training centre and risk map, clearly marked. Sets up a demo supervisor (PIN 1234) if there is none.",
        "प्रशिक्षण केंद्र और जोखिम नक्शे पर साफ़ निशान के साथ नमूना डेटा दिखाता है। कोई सुपरवाइज़र न हो तो डेमो सुपरवाइज़र (पिन 1234) बनाता है।",
    )
    val demoOn = T("Demo mode is on", "डेमो मोड चालू है")
    val demoOff = T("Demo mode is off", "डेमो मोड बंद है")
    val demoSupervisorCreated = T("Demo mode is on. Demo supervisor PIN: 1234", "डेमो मोड चालू है। डेमो सुपरवाइज़र पिन: 1234")
    val about = T("About", "ऐप के बारे में")
    val aboutBody = T(
        "AR safety training and offline certification for Jharkhand's mines and plants. SIH26041.",
        "झारखंड की खदानों और प्लांटों के लिए AR सुरक्षा प्रशिक्षण और ऑफ़लाइन प्रमाणन। SIH26041.",
    )
    val mapDataTitle = T("Map data", "नक्शे का डेटा")
    val mapAttribution = T(
        "District boundaries: DataMeet India community (Census 2011), CC BY 2.5 India, simplified.",
        "ज़िला सीमाएँ: DataMeet India समुदाय (जनगणना 2011), CC BY 2.5 India, सरल रूप में।",
    )
    val iconsTitle = T("Icons", "आइकन")
    val iconsBody = T("Material Icons by Google, Apache License 2.0.", "Google के Material Icons, Apache License 2.0.")

    // ------------------------------------------------------------ supervisor on this phone
    val supervisorSection = T("Supervisor on this phone", "इस फ़ोन पर सुपरवाइज़र")
    val supervisorNone = T("Not set up", "सेट नहीं है")
    val setUpSupervisor = T("Set up supervisor", "सुपरवाइज़र सेट करें")
    val changeSupervisor = T("Change supervisor", "सुपरवाइज़र बदलें")
    val supervisorName = T("Supervisor name", "सुपरवाइज़र का नाम")
    val newPin = T("New PIN (4 to 8 digits)", "नया पिन (4 से 8 अंक)")
    val repeatPin = T("Repeat the PIN", "पिन दोबारा डालें")
    val pinMismatch = T("The two PINs do not match.", "दोनों पिन मेल नहीं खाते।")
    val pinRule = T("The PIN must be 4 to 8 digits.", "पिन 4 से 8 अंकों का होना चाहिए।")
    val supervisorSaved = T("Supervisor saved on this phone.", "सुपरवाइज़र इस फ़ोन पर सहेजा गया।")
    val removeSupervisor = T("Remove supervisor", "सुपरवाइज़र हटाएँ")
    val removeSupervisorBody = T(
        "Drills on this phone can no longer be signed off until a supervisor is set up again.",
        "जब तक दोबारा सुपरवाइज़र सेट न हो, इस फ़ोन पर ड्रिल की पुष्टि नहीं हो सकेगी।",
    )
    val supervisorKey = T("Supervisor key", "सुपरवाइज़र कुंजी")
    val supervisorKeyHelp = T(
        "Give this key to the training centre so it can check your sign-offs.",
        "यह कुंजी प्रशिक्षण केंद्र को दें ताकि वह आपकी पुष्टि जाँच सके।",
    )

    // ------------------------------------------------------------ sample data
    val thisPhone = T("This phone", "यह फ़ोन")
    val sampleData = T("Sample", "नमूना")
    val sampleTitle = T("SAMPLE DATA", "नमूना डेटा")
    val sampleBody = T(
        "Made-up workers and reports for the demo. Not real, never saved, never sent.",
        "डेमो के लिए बनाए गए कर्मचारी और रिपोर्ट। असली नहीं, न सहेजे जाते हैं, न भेजे जाते हैं।",
    )

    // ------------------------------------------------------------ risk map
    val riskMapSubtitle = T("Jharkhand, by district", "झारखंड, ज़िलेवार")
    val riskNone = T("No data", "डेटा नहीं")
    val riskLow = T("Low", "कम")
    val riskMedium = T("Medium", "मध्यम")
    val riskHigh = T("High", "अधिक")
    val riskPoints = T("Risk points", "जोखिम अंक")
    val topDistricts = T("Highest risk", "सबसे अधिक जोखिम")
    val tapDistrict = T("Tap a district on the map to see why it has its colour.", "नक्शे पर किसी ज़िले पर टैप करें और देखें कि उसका रंग ऐसा क्यों है।")
    val howColour = T("How the colour is worked out", "रंग कैसे तय होता है")
    val riskRule = T(
        "Each high-severity hazard report adds 4 points, medium 2, low 1. Each STOP in a drill adds 2, each failed drill 1. Points halve every 30 days. Under 3 points is low, 3 to 8 medium, 8 or more high.",
        "हर अधिक गंभीर खतरे की रिपोर्ट 4 अंक जोड़ती है, मध्यम 2, कम 1। ड्रिल में हर STOP 2 अंक, हर असफल ड्रिल 1 अंक। अंक हर 30 दिन में आधे होते हैं। 3 से कम कम जोखिम, 3 से 8 मध्यम, 8 या अधिक अधिक जोखिम।",
    )
    val noRiskDataTitle = T("Nothing to map yet", "नक्शे के लिए अभी कुछ नहीं")
    val noRiskData = T("Hazard reports and drills from this phone will colour the map.", "इस फ़ोन की खतरा रिपोर्ट और ड्रिल से नक्शे में रंग भरेंगे।")
    val noRiskDataDemo = T("Switch to Sample above to see how the map looks with data.", "डेटा के साथ नक्शा देखने के लिए ऊपर नमूना चुनें।")
    val districtNoData = T("No reports or drills from this district yet.", "इस ज़िले से अभी कोई रिपोर्ट या ड्रिल नहीं।")
    val statReports = T("Hazard reports", "खतरा रिपोर्ट")
    val statHighReports = T("High severity", "अधिक गंभीर")
    val statDrills = T("Drills", "ड्रिल")
    val statStops = T("STOP actions", "STOP कदम")
    val failedDrills = T("Drills not passed", "पास न हुई ड्रिल")
    val reportedHazards = T("What was reported", "क्या रिपोर्ट हुआ")
    val latestReports = T("Latest reports", "ताज़ा रिपोर्ट")

    // ------------------------------------------------------------ training centre
    val trainingCentreSubtitle = T("Drills taken on this phone", "इस फ़ोन पर हुई ड्रिल")
    val statAttempts = T("Drills", "ड्रिल")
    val statPassRate = T("Passed", "पास")
    val statWorkers = T("Workers", "कर्मचारी")
    val statPending = T("Waiting to sync", "सिंक बाकी")
    val signOffs = T("Supervisor sign-off", "सुपरवाइज़र पुष्टि")
    val signedOffShare = T("drills signed off", "ड्रिल की पुष्टि हुई")
    val signOffWhy = T(
        "Only signed-off drills can become final certificates at the training centre.",
        "सिर्फ पुष्टि वाली ड्रिल ही प्रशिक्षण केंद्र पर अंतिम प्रमाणपत्र बन सकती हैं।",
    )
    val byModule = T("By module", "मॉड्यूल के अनुसार")
    val averageScore = T("average score", "औसत अंक")
    val skillsAcross = T("Skills across all drills", "सभी ड्रिल में कौशल")
    val skillsFloorNote = T("Green is at or above the pass mark for that skill.", "हरा मतलब उस कौशल के पास अंक या उससे ऊपर।")
    val mostMissed = T("Steps most often missed", "सबसे ज़्यादा चूके गए कदम")
    val mostMissedHelp = T("Scored under 70, out of all drills of that module. Teach these again.", "उस मॉड्यूल की सभी ड्रिल में 70 से कम अंक। इन्हें फिर से सिखाएँ।")
    val stopActions = T("STOP actions in drills", "ड्रिल में STOP कदम")
    val recentDrills = T("Recent drills", "हाल की ड्रिल")
    val noDrillsTitle = T("No drills on this phone yet", "इस फ़ोन पर अभी कोई ड्रिल नहीं")
    val noDrills = T("Finished drills show up here, with what each worker got right and wrong.", "पूरी हुई ड्रिल यहाँ दिखेंगी, हर कर्मचारी ने क्या सही और क्या गलत किया।")
    val noDrillsDemo = T("Switch to Sample above to see the dashboard with data.", "डेटा के साथ डैशबोर्ड देखने के लिए ऊपर नमूना चुनें।")
}
