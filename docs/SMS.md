# Android SMS Reading & Cloud Sync Implementation Guide

Developing an Android app to **read SMS messages and upload them to a cloud backend** involves handling permissions, accessing SMS data, and managing background tasks. Below is a step-by-step guide covering required permissions, SMS retrieval (existing and new messages), OS restrictions in recent Android versions, and best practices for syncing with a cloud service.

## Permissions and Manifest Setup

To read SMS messages and listen for incoming SMS, you need to declare specific permissions in your **AndroidManifest.xml** and request them at runtime:

- **SMS Reading Permissions:** Add &lt;uses-permission android:name="android.permission.READ_SMS" /&gt; to read existing SMS from the device’s inbox[\[1\]](https://lindevs.com/read-sms-messages-in-android#:~:text=Application%20should%20have%20access%20to,permission%20in%20the%20manifest%20file). Also include &lt;uses-permission android:name="android.permission.RECEIVE_SMS" /&gt; to receive SMS broadcast events[\[2\]](https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html#:~:text=%3Cuses,). These permissions belong to the **SMS permission group** and are considered **dangerous**, meaning the user must grant them at runtime on Android 6.0+.
- **Network Permission:** Include &lt;uses-permission android:name="android.permission.INTERNET" /&gt; so the app can communicate with the cloud backend.
- **Telephony Feature (Optional):** You may declare the telephony hardware feature. For example:  

- &lt;uses-feature android:name="android.hardware.telephony" android:required="false"/&gt;
- This indicates the app can use telephony features but is still installable on devices without telephony (the app would simply have no SMS to read on such devices).

Your manifest might look like this (in part):

&lt;manifest ...&gt;  
&lt;!-- Permissions --&gt;  
&lt;uses-permission android:name="android.permission.READ_SMS" /&gt;  
&lt;uses-permission android:name="android.permission.RECEIVE_SMS" /&gt;  
&lt;uses-permission android:name="android.permission.INTERNET" /&gt;  
<br/>&lt;!-- Optional: declare telephony feature --&gt;  
&lt;uses-feature android:name="android.hardware.telephony" android:required="false" /&gt;  
<br/>&lt;application ...&gt;  
...  
&lt;!-- BroadcastReceiver for incoming SMS --&gt;  
<receiver android:name=".SmsReceiver"  
android:exported="true"  
android:permission="android.permission.BROADCAST_SMS">  
&lt;intent-filter&gt;  
&lt;action android:name="android.provider.Telephony.SMS_RECEIVED" /&gt;  
&lt;/intent-filter&gt;  
&lt;/receiver&gt;  
...  
&lt;/application&gt;  
&lt;/manifest&gt;

In the above manifest snippet, we define a SmsReceiver (a BroadcastReceiver subclass) to listen for incoming SMS (more on this in a later section). We set android:exported="true" to allow receiving external broadcasts (from the system), and use android:permission="android.permission.BROADCAST_SMS" on the receiver to ensure that only the system (which holds the BROADCAST_SMS permission) can trigger our SMS receiver[\[3\]](https://stackoverflow.com/questions/48789572/oreo-broadcastreceiver-sms-received-not-working#:~:text=Just%20add). This prevents other apps from spoofing the SMS broadcast.

**Runtime Permission Requests:** Declaring READ_SMS and RECEIVE_SMS in the manifest is not enough on modern Android; the app must also prompt the user to grant these permissions at runtime. You should check for the permissions in code and, if not granted, request them via ActivityCompat.requestPermissions(). For example, in your Activity’s onCreate:

if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)  
!= PackageManager.PERMISSION_GRANTED ||  
ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)  
!= PackageManager.PERMISSION_GRANTED) {  
<br/>ActivityCompat.requestPermissions(  
this,  
arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),  
REQUEST_CODE_SMS_PERMISSIONS  
)  
}

**Note:** Both **READ_SMS** and **RECEIVE_SMS** should be requested. As of Android 8.0 (API 26) and higher, permissions are no longer granted in groups implicitly[\[4\]](https://stackoverflow.com/questions/48789572/oreo-broadcastreceiver-sms-received-not-working#:~:text=requestPermissions%28new%20String%5B%5D). This means even though both permissions are in the SMS group, you must ask for each one explicitly. Ensure you handle the user's response in onRequestPermissionsResult. If the user denies the SMS permissions, the app cannot access the SMS inbox or receive SMS events, so you may need to explain why the permission is needed and prompt again (with proper user consent).

## Reading SMS from the Device Inbox

To fetch existing SMS messages on the device (e.g. all messages in the inbox), use the Android **Telephony content provider**. Android’s Telephony provider stores SMS and MMS messages in a central database, accessible via a content URI[\[5\]](https://lindevs.com/read-sms-messages-in-android#:~:text=Android%20allows%20to%20read%20SMS,is%20a%20content%20provider%20component). With the READ_SMS permission granted, your app can query this provider to retrieve SMS details.

**Querying the SMS Inbox:** The content URI for SMS inbox messages is content://sms/inbox. The Android SDK provides constants in Telephony.Sms and related classes to build queries. For example, you can use Telephony.Sms.Inbox.CONTENT_URI as the URI for inbox SMS. A typical query might retrieve columns such as the sender address, message body, timestamp, and message type. The message “type” field distinguishes incoming vs. sent messages (e.g. type=1 for inbox, type=2 for sent messages[\[6\]](https://lindevs.com/read-sms-messages-in-android#:~:text=val%20numberCol%20%3D%20Telephony,Sent)). Since we only need inbox messages, we can filter by type or use the Inbox URI which inherently targets received messages.

Below is a code snippet (Kotlin) demonstrating how to read SMS from the inbox:

val projection = arrayOf(  
Telephony.Sms.ADDRESS, // sender phone number  
Telephony.Sms.BODY, // message text  
Telephony.Sms.DATE, // timestamp (in ms since epoch)  
Telephony.Sms.Read, // read status, etc. (optional)  
Telephony.Sms.TYPE // message type: 1=INBOX, 2=SENT, etc.  
)  
val uri = Telephony.Sms.Inbox.CONTENT_URI // URI for inbox SMS  
val cursor = contentResolver.query(uri, projection, null, null, null)  
if (cursor != null) {  
val addrIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)  
val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)  
val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)  
val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)  
while (cursor.moveToNext()) {  
val sender = cursor.getString(addrIndex)  
val message = cursor.getString(bodyIndex)  
val timestamp = cursor.getLong(dateIndex)  
val msgType = cursor.getInt(typeIndex)  
// Process or store the SMS (e.g., add to a list for uploading)  
}  
cursor.close()  
}

This will iterate through all SMS in the device's inbox. Each message’s sender (ADDRESS) and text body (BODY) can be read from the cursor[\[7\]](https://lindevs.com/read-sms-messages-in-android#:~:text=val%20cursor%20%3D%20contentResolver,projection%2C%20null%2C%20null%2C%20null). You might also retrieve other columns like Telephony.Sms.DATE (which gives you when the SMS was received, as a Unix timestamp in milliseconds) and any other relevant metadata. Make sure to perform this query **off the main thread** (for example, using a background thread, coroutine, or WorkManager) to avoid blocking the UI, since there could be many messages to read.

**Important:** If the device uses **Rich Communication Services (RCS)** or chat features for messaging, some conversations may not appear in the SMS content provider (they aren’t traditional SMS). Our app will only have access to actual SMS messages stored on the device. For traditional SMS, the above query will retrieve them as expected.

## Monitoring Incoming SMS in the Background

To detect new incoming SMS messages as they arrive, implement a **BroadcastReceiver** that listens for the system SMS broadcast. Android broadcasts an intent for incoming SMS with action "android.provider.Telephony.SMS_RECEIVED". By registering a receiver for this action, your app can get notified whenever a new SMS is delivered.

**Manifest-Registered Receiver:** As shown in the manifest snippet earlier, declare a &lt;receiver&gt; with an &lt;intent-filter&gt; for the SMS_RECEIVED action. The system will invoke this receiver whenever an SMS is received, even if your app is not currently running (this is allowed as an exception to Android 8.0’s implicit broadcast limitations[\[8\]](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions#:~:text=occurrence%2C%20and%20are%20usually%20under,the%20user%E2%80%99s%20control)). For example:

<receiver android:name=".SmsReceiver" android:exported="true"  
android:permission="android.permission.BROADCAST_SMS">  
&lt;intent-filter&gt;  
&lt;action android:name="android.provider.Telephony.SMS_RECEIVED"/&gt;  
&lt;/intent-filter&gt;  
&lt;/receiver&gt;

With this in place (and after the user has granted the SMS permissions), the SmsReceiver.onReceive() will be called on an incoming message. Below is an example implementation of the BroadcastReceiver in Kotlin:

class SmsReceiver : BroadcastReceiver() {  
override fun onReceive(context: Context, intent: Intent) {  
if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {  
// Extract SMS messages from the intent  
val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)  
for (sms in smsMessages) {  
val sender = sms.displayOriginatingAddress // sender's phone number  
val body = sms.messageBody // text of the SMS  
// TODO: Handle the new SMS (e.g., save to local storage or upload immediately)  
}  
}  
}  
}

In this code, Telephony.Sms.Intents.getMessagesFromIntent(intent) is a convenient method that parses the SMS PDUs from the intent and returns an array of SmsMessage objects. This accounts for multi-part SMS (concatenated messages) by providing all parts in the array. We loop through smsMessages to get the full text (messageBody) and originating address of each SMS. At this point, you can initiate uploading this message to your cloud backend or store it for later syncing.

**Background Execution Considerations:** A BroadcastReceiver’s onReceive runs on the main thread and must finish quickly. You **should not perform network operations directly inside** onReceive because the system could kill your app if the receiver takes too long. Instead, use the receiver to kick off background work for the upload: - One approach is to start a background Service (or IntentService) to handle the upload. However, on newer Android versions you cannot start a background service directly from an implicit broadcast unless you make it a foreground service with a user-visible notification. - A modern and recommended approach is to use **WorkManager** or **JobScheduler** to schedule a background job for the upload. You can enqueue a one-time WorkManager task from within onReceive to handle the network operation asynchronously.

For example, using WorkManager (Jetpack library):

// Inside SmsReceiver.onReceive, for each new SMS:  
val uploadWork = OneTimeWorkRequestBuilder&lt;SmsUploadWorker&gt;()  
.setInputData(workDataOf(  
"sender" to sender,  
"message" to body,  
"timestamp" to System.currentTimeMillis() // example extra data  
))  
.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())  
.build()  
WorkManager.getInstance(context).enqueue(uploadWork)

Here SmsUploadWorker would be a custom Worker class that retrieves the SMS data from inputData and performs the API call to upload the message to your cloud. We also specify a network constraint so the work only runs when internet is available.

**Note:** Registering the receiver in the manifest (static registration) is crucial for background SMS monitoring. A dynamically registered receiver (via registerReceiver in an Activity) would only work while the app is running. The manifest-declared receiver ensures the system will wake your app to deliver SMS broadcasts. The SMS broadcast is delivered to all apps with matching receivers (not just the default SMS app), as long as they have the proper permissions[\[8\]](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions#:~:text=occurrence%2C%20and%20are%20usually%20under,the%20user%E2%80%99s%20control). Your app will receive the SMS _in addition_ to the user’s default messaging app; it cannot prevent the SMS from reaching the default app (and should not attempt to, since our goal is only to read/upload, not to intercept or block the message).

## Android Version Constraints & SMS Restrictions

Modern Android versions impose several constraints related to background processing and SMS access. It’s important to design your app with these in mind:

- **Background Execution Limits (Android 8.0+):** Starting with Oreo (API 26), apps cannot run arbitrary background services indefinitely. If your app needs to do work in the background (like syncing SMS to cloud), you must use APIs like WorkManager, JobScheduler, or foreground services. WorkManager is the preferred API for most persistent background tasks[\[9\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=WorkManager%20is%20the%20recommended%20solution,recommended%20API%20for%20background%20processing) because it schedules work in a battery- and OS-friendly manner. It also **adheres to power-saving features (like Doze mode)** automatically[\[10\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=Scheduled%20work%20is%20stored%20in,is%20rescheduled%20across%20device%20reboots), ensuring your sync tasks will eventually run without you having to manage exact wake-up times.
- **Implicit Broadcast Restrictions:** Oreo also restricted most implicit broadcasts (to avoid waking up too many apps), but **SMS_RECEIVED is exempt** from this restriction[\[8\]](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions#:~:text=occurrence%2C%20and%20are%20usually%20under,the%20user%E2%80%99s%20control). This means your manifest-registered SMS receiver will still work on Android 8.0+ without needing special workarounds. (Other broadcasts may not fire unless the app is active, but SMS_RECEIVED and related telephony broadcasts are allowed because they are critical for messaging apps.)
- **SMS Permission and Default Apps (Android 4.4+ and Play Store Policy):** Android 4.4 (KitKat) introduced the concept of a **default SMS app**. Only the user’s chosen default SMS app is allowed to write to the SMS provider or delete messages, and it has the highest priority for receiving SMS. Non-default apps can still **read** SMS (with permission) and receive the SMS_RECEIVED broadcast, but cannot abort the broadcast or write/delete SMS. Additionally, Google Play has strict **SMS permission policies** to protect user privacy[\[11\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=Several%20core%20device%20functions%2C%20such,related%20permission%20groups)[\[12\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=If%20you%20distribute%20your%20app,app%20satisfies%20an%20exception%20case). If you plan to publish on the Play Store, you typically **must declare your app as a “SMS handler” (default SMS app)** or qualify for an exception in order to use SMS permissions. For example, Google’s policy states that an app should not request SMS reading unless it's a core function of the app (like a messaging app or a backup app), and an app **must prompt to become the default SMS handler before requesting SMS permissions**[\[13\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=the%20guidance%20on%20using%20SMS,permission). In practice, this means you might need to implement sending functionality and present a dialog for the user to switch their default SMS app to yours, if full SMS access is not otherwise allowed. If your app is not meant to replace the SMS app and is just uploading messages (e.g. for backup or analysis), you would need to fall under a permitted use case in Play’s policy (or distribute the app outside the Play Store).
- **Privacy and User Consent:** Even if there are no strict privacy requirements from your side, remember that reading a user’s SMS is highly sensitive. Starting Android 10, the system also emphasizes privacy by requiring explicit permission grants and showing the user indicators when SMS is accessed. Always ensure the user understands why the permission is needed. (For example, you might show a dialog or onboarding screen explaining that the app will read and back up their text messages.) The system’s runtime permission dialog will prompt the user to allow SMS access, and the user can also revoke this permission later in system settings. Your app should be robust against the permission being denied or revoked (e.g. by disabling the syncing functionality or gracefully notifying the user).
- **Doze Mode and App Standby (Android 6.0+):** In extended periods of device idle, network access for background apps can be deferred. WorkManager helps by scheduling sync jobs to run in maintenance windows when possible[\[10\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=Scheduled%20work%20is%20stored%20in,is%20rescheduled%20across%20device%20reboots). If you try to upload immediately upon receiving an SMS while the device is in deep doze, the upload might be delayed until the device wakes or a maintenance window opens. This is usually fine for a backup scenario (a short delay is acceptable), but it’s something to be aware of. Using WorkManager with the appropriate constraints (e.g. network connectivity) is a robust way to handle this, as it will run the work as soon as conditions allow.

In summary, modern Android requires that you use the appropriate frameworks for background work and be mindful of permission policies. Our approach (using a broadcast receiver + WorkManager) is aligned with these requirements: the receiver responds to the high-priority SMS event, and WorkManager handles deferred background syncing in a compliant way.

## Best Practices for Syncing to a Cloud Backend

Once your app has access to SMS messages (existing and new), the next step is to upload them to a cloud service. While the specifics of the backend are outside our scope, here are best practices for the Android side of syncing:

- **Perform Sync in Background:** Never block the UI thread for network operations. Use WorkManager, coroutines, or an IntentService/background thread to perform the upload. WorkManager is highly recommended for reliable background sync tasks – it ensures work persists across app restarts and even device reboots[\[9\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=WorkManager%20is%20the%20recommended%20solution,recommended%20API%20for%20background%20processing)[\[14\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=,application%20data%20with%20a%20server). For example, you can schedule periodic syncs (e.g. daily backup of SMS) using PeriodicWorkRequest, or one-time syncs (like initial bulk upload) using OneTimeWorkRequest. WorkManager also provides **robust scheduling and retry policies** (exponential backoff on failures, etc.)[\[15\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=Flexible%20retry%20policy) which are useful for network operations.
- **Use Network Constraints:** Tie your sync jobs to network availability. As shown in the snippet earlier, you can specify setRequiredNetworkType(NetworkType.CONNECTED) for WorkManager, or check ConnectivityManager before attempting an upload. This prevents unnecessary failures when offline. You might also consider requiring unmetered Wi-Fi if the dataset is large (to avoid mobile data charges).
- **Batch and Reduce Data Transfers:** If uploading a large number of SMS (e.g., the entire inbox on first run), consider batching the data or compressing it. For instance, rather than calling the API for each SMS in a loop, you might accumulate messages and send them in a single request (if the backend API supports bulk upload). This reduces overhead and is more efficient. If sending individually, implement some rate limiting or small delays to avoid spamming the server.
- **Maintain Sync State:** To avoid re-uploading the same messages repeatedly, keep track of which SMS have been uploaded. You could store the last synced message ID or timestamp in SharedPreferences or a small local database. Each time you sync, query only for messages newer than the last synced one. The SMS content provider has an \_id for each message and a DATE field; using these, you can identify new messages since the last sync. Similarly, for initial sync, once done, mark all as synced. Maintaining this state prevents duplicate uploads and can significantly reduce bandwidth and processing.
- **Handle Failures and Retries:** Network requests can fail due to connectivity issues or server errors. Implement retry logic. If using WorkManager, you can leverage its retry mechanism (return Result.retry() from the Worker if the upload didn’t succeed, so it tries again later). Ensure that if the app is killed or the device restarts, any pending uploads resume – WorkManager takes care of this automatically by rescheduling your work.
- **Security and Transport:** Even though “no security requirements” are specified by the user, it’s good practice to use secure communication (HTTPS) when sending SMS contents to the cloud. This protects the sensitive message data in transit. Also, if the backend requires authentication (tokens, API keys), handle those securely (e.g. use Android’s AccountManager or Google Sign-In for user-specific tokens, and do not hard-code secrets in the app).
- **User Consent & Transparency:** If the app is uploading personal SMS to a server, ensure the user is aware. Even if you have the permission, it’s good UX to perhaps provide a toggle or a clear note that “Your text messages will be periodically backed up to the cloud.” This ties into privacy considerations but also ensures the user isn’t surprised by background data usage. From a technical standpoint, you might allow the user to initiate the first sync manually or show a notification when a bulk upload is happening, especially if it’s large.
- **Testing on Recent Android Versions:** Make sure to test your implementation on the latest Android devices (Android 13/14 and above) because behaviors can change. For example, verify that your SMS reading logic works on these versions. (As of Android 13+, standard SMS with permissions should work, but always double-check in case any manufacturer-specific restrictions apply.)

In practice, a good pattern is: 1. On first launch, after permissions are granted, schedule a **one-time job** to read all existing SMS and upload them (this could be done in chunks if there are thousands of messages). Update the last synced marker. 2. For new SMS, handle each via the broadcast receiver by immediately enqueueing a small background job to upload that single message (or writing it to a local cache that is synced periodically). 3. Optionally, schedule a periodic job (e.g. daily) to catch any changes or just re-verify sync (this might catch messages that were missed if the app wasn’t running or permission was temporarily revoked and re-granted).

By following these practices, your Android app will reliably collect SMS messages and hand them off to the cloud backend for storage.

## User Consent and Runtime Prompts

Finally, ensure that the app **properly handles user consent** for SMS access: - The first time the app runs (or whenever you need to access SMS and the permission isn’t yet granted), the user will see a system prompt: **“Allow \\&lt;AppName&gt; to read SMS messages?”**. You should ideally have given the user context before this prompt. For example, a simple explanation screen that this app needs to read their SMS to back them up to cloud will prepare the user for the permission dialog. This can improve acceptance rates. - If the user accepts, you can proceed with reading/uploading SMS. If they deny, you should disable the SMS sync functionality and perhaps show a message that the app cannot function without that permission. You can also provide a **“Try again”** option with a rationale: if the user denied with “Don’t ask again”, you’d have to direct them to settings to enable it. - No permission is needed for sending SMS in this app (since we are not sending any), which simplifies things. We also do not need special privacy-sensitive permissions beyond SMS. - Keep in mind that on Android 13+, notifications require a separate permission. If your app plans to notify the user (e.g., “Backup completed” notification), you should also request the POST_NOTIFICATIONS permission at runtime. This wasn’t explicitly asked, but it’s a new consideration in recent Android if you use notifications for status.

In summary, the Android portion of this system requires careful handling of SMS permissions and background execution. You declare the appropriate permissions in the manifest and request them at runtime[\[16\]](https://lindevs.com/read-sms-messages-in-android#:~:text=Using%20the%20method%20,ActivityCompat.requestPermissions), use the Telephony provider to read existing messages, and register a BroadcastReceiver to capture new messages as they arrive. Then you offload work to background threads or WorkManager for syncing. Be mindful of Android’s restrictions on background work and SMS permissions – by using WorkManager and adhering to Google’s policies (if distributing publicly), you can ensure your SMS reading and uploading functionality works smoothly on modern Android devices.

**Sources:**

- Android Telephony SMS Provider usage[\[6\]](https://lindevs.com/read-sms-messages-in-android#:~:text=val%20numberCol%20%3D%20Telephony,Sent)[\[7\]](https://lindevs.com/read-sms-messages-in-android#:~:text=val%20cursor%20%3D%20contentResolver,projection%2C%20null%2C%20null%2C%20null) (reading inbox messages via content resolver)
- Android Developers Documentation on SMS runtime permissions and default SMS app policies[\[12\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=If%20you%20distribute%20your%20app,app%20satisfies%20an%20exception%20case)[\[13\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=the%20guidance%20on%20using%20SMS,permission)
- Android Developers Documentation on broadcast receiver limitations (SMS_RECEIVED exemption)[\[8\]](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions#:~:text=occurrence%2C%20and%20are%20usually%20under,the%20user%E2%80%99s%20control)
- Android Developers Documentation on WorkManager for background tasks[\[9\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=WorkManager%20is%20the%20recommended%20solution,recommended%20API%20for%20background%20processing)[\[14\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=,application%20data%20with%20a%20server)
- Google Codelab/Training on receiving SMS with BroadcastReceiver (permission requirements)[\[2\]](https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html#:~:text=%3Cuses,)[\[17\]](https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html#:~:text=Broadcast%20Actions,intent)
- Stack Overflow discussion on secure SMS receiver implementation (permissions and BroadcastReceiver setup)[\[3\]](https://stackoverflow.com/questions/48789572/oreo-broadcastreceiver-sms-received-not-working#:~:text=Just%20add)[\[4\]](https://stackoverflow.com/questions/48789572/oreo-broadcastreceiver-sms-received-not-working#:~:text=requestPermissions%28new%20String%5B%5D)

[\[1\]](https://lindevs.com/read-sms-messages-in-android#:~:text=Application%20should%20have%20access%20to,permission%20in%20the%20manifest%20file) [\[5\]](https://lindevs.com/read-sms-messages-in-android#:~:text=Android%20allows%20to%20read%20SMS,is%20a%20content%20provider%20component) [\[6\]](https://lindevs.com/read-sms-messages-in-android#:~:text=val%20numberCol%20%3D%20Telephony,Sent) [\[7\]](https://lindevs.com/read-sms-messages-in-android#:~:text=val%20cursor%20%3D%20contentResolver,projection%2C%20null%2C%20null%2C%20null) [\[16\]](https://lindevs.com/read-sms-messages-in-android#:~:text=Using%20the%20method%20,ActivityCompat.requestPermissions) Read SMS Messages in Android | Lindevs

<https://lindevs.com/read-sms-messages-in-android>

[\[2\]](https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html#:~:text=%3Cuses,) [\[17\]](https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html#:~:text=Broadcast%20Actions,intent) 2.2: Sending and Receiving SMS Messages - Part 2 · GitBook

<https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html>

[\[3\]](https://stackoverflow.com/questions/48789572/oreo-broadcastreceiver-sms-received-not-working#:~:text=Just%20add) [\[4\]](https://stackoverflow.com/questions/48789572/oreo-broadcastreceiver-sms-received-not-working#:~:text=requestPermissions%28new%20String%5B%5D) android - Oreo BroadcastReceiver SMS Received not working - Stack Overflow

<https://stackoverflow.com/questions/48789572/oreo-broadcastreceiver-sms-received-not-working>

[\[8\]](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions#:~:text=occurrence%2C%20and%20are%20usually%20under,the%20user%E2%80%99s%20control) Implicit broadcast exceptions  |  Background work  |  Android Developers

<https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions>

[\[9\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=WorkManager%20is%20the%20recommended%20solution,recommended%20API%20for%20background%20processing) [\[10\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=Scheduled%20work%20is%20stored%20in,is%20rescheduled%20across%20device%20reboots) [\[14\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=,application%20data%20with%20a%20server) [\[15\]](https://developer.android.com/topic/libraries/architecture/workmanager#:~:text=Flexible%20retry%20policy) App Architecture: Data Layer - Schedule Task with WorkManager - Android Developers  |  App architecture

<https://developer.android.com/topic/libraries/architecture/workmanager>

[\[11\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=Several%20core%20device%20functions%2C%20such,related%20permission%20groups) [\[12\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=If%20you%20distribute%20your%20app,app%20satisfies%20an%20exception%20case) [\[13\]](https://developer.android.com/guide/topics/permissions/default-handlers#:~:text=the%20guidance%20on%20using%20SMS,permission) Permissions used only in default handlers  |  Privacy  |  Android Developers

<https://developer.android.com/guide/topics/permissions/default-handlers>
