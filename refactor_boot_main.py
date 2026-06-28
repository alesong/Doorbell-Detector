import re

# BootReceiver.kt
with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/receiver/BootReceiver.kt', 'r') as f:
    content = f.read()

content = re.sub(r'            val serviceIntent = Intent\(context, NotificationListener::class.java\).*?Log\.d\(TAG, "Service started after boot"\)\s*\} catch \(e: Exception\) \{\s*Log\.e\(TAG, "Failed to start service after boot", e\)\s*\}', '            Log.d(TAG, "Boot completed. The system will start NotificationListenerService automatically if permission is granted.")', content, flags=re.DOTALL)

with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/receiver/BootReceiver.kt', 'w') as f:
    f.write(content)


# MainScreen.kt
with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/ui/MainScreen.kt', 'r') as f:
    content = f.read()

content = re.sub(r'                            try \{\s*context\.stopService\(intent\)\s*if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.O\) \{\s*context\.startForegroundService\(intent\)\s*\} else \{\s*context\.startService\(intent\)\s*\}\s*\} catch \(e: Exception\) \{\s*android\.util\.Log\.e\("MainScreen", "startService failed", e\)\s*\}', '                            // Let the system handle the service via rebind request if needed.\n                            try { NotificationListener.requestRebind(android.content.ComponentName(context, NotificationListener::class.java)) } catch (e: Exception) {}', content, flags=re.DOTALL)

with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/ui/MainScreen.kt', 'w') as f:
    f.write(content)
