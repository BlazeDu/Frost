package com.storm.coldwind

object MenuPages {
    fun getPages(): MutableList<MenuPage> {
        return mutableListOf(
            // ========== 功能1页面 ==========
            MenuPage(
                id = "page_1",
                title = "功能1",
                items = mutableListOf(
                    MenuItem.Switch("offline_mode", "离线模式", false),
                    MenuItem.Switch("infinite_energy", "无限能量", false)
                )
            ),
            // ========== 功能2页面 ==========
            MenuPage(
                id = "page_2",
                title = "功能2",
                items = mutableListOf(
                    MenuItem.Selector("game_speed", "游戏倍速",
                        listOf("默认", "二倍", "四倍", "八倍"), "默认"),
                    MenuItem.Counter("wing_count", "光翼数量", 0),
                    MenuItem.Counter("flight_boost", "飞行强化", 0)
                )
            ),
            // ========== 功能3页面 ==========
            MenuPage(
                id = "page_3",
                title = "功能3",
                items = mutableListOf(
                    MenuItem.Counter("jump_boost", "跳跃强化", 0),
                    MenuItem.Counter("function3", "功能3", 0)
                )
            ),
            // ========== 功能4页面 ==========
            MenuPage(
                id = "page_4",
                title = "功能4",
                items = mutableListOf(
                    MenuItem.Counter("function4_1", "功能4-1", 0),
                    MenuItem.Counter("function4_2", "功能4-2", 0),
                    MenuItem.Switch("function4_switch", "功能4开关", false)
                )
            )
        )
    }
}