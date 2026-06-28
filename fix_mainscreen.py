import re
with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/ui/MainScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('NotificationListener.requestRebind', 'android.service.notification.NotificationListenerService.requestRebind')

with open('/home/aleson/Doorbell Detector/android/app/src/main/java/com/doorbell/detector/ui/MainScreen.kt', 'w') as f:
    f.write(content)
