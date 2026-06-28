import re
with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/service/NotificationListener.kt', 'r') as f:
    content = f.read()

# Remove heartbeat and poll runnables
content = re.sub(r'    private val heartbeatRunnable = object : Runnable \{.*?(?=    override fun onCreate\(\))', '', content, flags=re.DOTALL)

# Remove startForegroundService from onCreate
content = re.sub(r'        val intent = Intent\(this, NotificationListener::class.java\)\s*if \(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O\) \{\s*startForegroundService\(intent\)\s*\} else \{\s*startService\(intent\)\s*\}', '', content)

# Remove onStartCommand entirely
content = re.sub(r'    override fun onStartCommand\(intent: Intent\?, flags: Int, startId: Int\): Int \{.*?(?=    override fun onBind)', '', content, flags=re.DOTALL)

# Remove startForegroundSafe calls
content = content.replace('        startForegroundSafe()', '')

# Remove handler and runnable usage
content = content.replace('        mainHandler.post(pollRunnable)', '')
content = content.replace('        mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)', '')
content = content.replace('        mainHandler.removeCallbacks(heartbeatRunnable)', '')
content = content.replace('        mainHandler.removeCallbacks(pollRunnable)', '')

# Remove startForegroundSafe function definition
content = re.sub(r'    private fun startForegroundSafe\(\) \{.*?(?=    private fun loadFromDataStore)', '', content, flags=re.DOTALL)

# Remove Handler and seenNotifKeys
content = re.sub(r'    private val mainHandler = Handler\(Looper.getMainLooper\(\)\)', '', content)
content = re.sub(r'        private val seenNotifKeys = mutableSetOf<String>\(\)', '', content)

# Write back
with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/service/NotificationListener.kt', 'w') as f:
    f.write(content)
