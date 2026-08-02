package com.fengnian.folderdrawer

/**
 * App Dialer 作为「特殊抽屉」并入现有抽屉体系时使用的约定常量。
 *
 * 特殊 id -2 不写入数据库（真实抽屉 id 从 1 自增，永不碰撞），App Dialer 以
 * 「虚拟抽屉」形式存在：主列表追加一张虚拟卡片、路由识别该 id 直接打开拨号盘、
 * 快捷方式经 CollectionLauncherActivity 的 folderdrawer://collection/-2 打开。
 *
 * 这样所有入口（主列表、钉桌面、手势软件动态发现、添加快捷方式）都复用普通抽屉
 * 那条已验证的 CollectionLauncherActivity 链路，绕开 DialerLauncherActivity
 * 这条 NoDisplay 独立链路在部分 ROM 上「调用假死」的问题。
 */
object DialerConstants {
    const val DIALER_COLLECTION_ID = -2L
}
