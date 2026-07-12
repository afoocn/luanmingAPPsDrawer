import io, sys

ROOT = "C:/Users/CHONGJING/WorkBuddy/2026-07-08-10-17-37/CollectionDrawerNEW"

def patch_file(relpath, replacements):
    p = ROOT + "/" + relpath
    with io.open(p, "r", encoding="utf-8") as f:
        data = f.read()
    for old, new, cnt in replacements:
        have = data.count(old)
        if have != cnt:
            raise SystemExit(f"[{relpath}] expected {cnt} occurrence(s) of:\n{old!r}\nbut found {have}")
        data = data.replace(old, new)
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(data)
    print(f"OK  {relpath}")

# 1) themes.xml : floating -> translucent + transparent status/nav bar
patch_file("app/src/main/res/values/themes.xml", [
    (
        '        <item name="android:windowIsFloating">true</item>\n',
        '        <item name="android:windowIsTranslucent">true</item>\n',
        1
    ),
    (
        '        <!-- 禁用窗口预览（starting window），避免启动时闪屏 -->\n'
        '        <item name="android:windowDisablePreview">true</item>\n',
        '        <!-- 禁用窗口预览（starting window），避免启动时闪屏 -->\n'
        '        <item name="android:windowDisablePreview">true</item>\n'
        '        <!-- 状态栏/导航栏透出宿主（透明 + 浅色图标），本窗口不重绘 -> 不闪 -->\n'
        '        <item name="android:statusBarColor">@android:color/transparent</item>\n'
        '        <item name="android:navigationBarColor">@android:color/transparent</item>\n'
        '        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>\n'
        '        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>\n',
        1
    ),
])

# 2) layout : root FrameLayout height match_parent -> wrap_content
patch_file("app/src/main/res/layout/activity_quick_launch_dialog.xml", [
    (
        '    android:layout_width="match_parent"\n'
        '    android:layout_height="match_parent"\n'
        '    android:background="#00000000">\n',
        '    android:layout_width="match_parent"\n'
        '    android:layout_height="wrap_content"\n'
        '    android:background="#00000000">\n',
        1
    ),
])

# 3) QuickLaunchDialogActivity : update stale "全屏半透明窗口" comment in applyCardBackground
patch_file("app/src/main/java/com/fengnian/folderdrawer/QuickLaunchDialogActivity.kt", [
    (
        '            // 全屏半透明窗口：毛玻璃由 Window.setBackgroundBlurRadius 作用于整屏背后，\n'
        '            // 根布局保持透明，让磨砂后的真实背景透出\n',
        '            // 半透明紧凑窗口：Window.setBackgroundBlurRadius 只磨砂「窗口背后那块」=卡片，\n'
        '            // 卡片透明 + 半透明 colorOverlay 让磨砂后的真实背景透出（iOS 同款）；屏幕其余不磨砂\n',
        1
    ),
])

# 4) build.gradle.kts : bump version
patch_file("app/build.gradle.kts", [
    ('        versionCode = 85\n', '        versionCode = 86\n', 1),
    ('        versionName = "2.03"\n', '        versionName = "2.04"\n', 1),
])

print("ALL PATCHES APPLIED")
