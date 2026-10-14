# ============================================================
# 单词速记 - ProGuard/R8 混淆规则
# ============================================================

# ---------- Room ----------
# Room 生成的实现类通过反射加载，必须保留
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# ---------- Kotlin 元数据 ----------
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ---------- 平移动画/反射相关 ----------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# ---------- 数据模型（Gson/JSON 解析词库时用） ----------
-keep class com.wordflash.app.data.seed.** { *; }
-keep class com.wordflash.app.domain.model.** { *; }

# ---------- 协程 ----------
-dontwarn kotlinx.coroutines.**

# ---------- 关闭部分日志（可选） ----------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
