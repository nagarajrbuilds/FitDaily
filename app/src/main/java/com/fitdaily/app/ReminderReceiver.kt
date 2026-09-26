package com.fitdaily.app
import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
class ReminderReceiver: BroadcastReceiver(){
 override fun onReceive(c:Context,i:Intent){
  val type=i.getStringExtra("type")?:"health"
  val text=when(type){"workout"->"Time for your planned workout.";"water"->"A quick hydration check.";"sleep"->"Start winding down for sleep.";else->"Open FitDaily."}
  val n=NotificationCompat.Builder(c,"fitdaily").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("FitDaily").setContentText(text).setAutoCancel(true).build()
  (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(type.hashCode(),n)
 }
}