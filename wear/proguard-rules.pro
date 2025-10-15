# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Wearable Data API classes
-keep class com.google.android.gms.wearable.** { *; }

# Keep alarm-related classes
-keep class me.tewodros.vibecalendaralarm.wear.WearAlarmReceiver { *; }
-keep class me.tewodros.vibecalendaralarm.wear.WearReminderActivity { *; }
