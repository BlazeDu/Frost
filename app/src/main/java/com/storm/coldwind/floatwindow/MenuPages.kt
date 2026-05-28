package com.storm.coldwind.floatwindow

object MenuPages {
    fun getPages(): MutableList<Menu> {
        return mutableListOf(
            // ========== 功能1页面 ==========
            Menu(
                id = "page_1",
                title = "功能1",
                items = mutableListOf(
                    Item.Switch("offline_mode", "离线模式", false),
                    Item.Divider,
                    Item.Switch("infinite_energy", "无限能量", false)
                )
            ),
            // ========== 功能2页面 ==========
            Menu(
                id = "page_2",
                title = "功能2",
                items = mutableListOf(
                    Item.Selector("game_speed", "游戏倍速",
                        listOf("默认", "二倍", "四倍", "八倍"), "默认"),
                    Item.Counter("wing_count", "数量", 0),
                    Item.Counter("flight_boost", "飞行强化", 0)
                )
            ),
            // ========== 功能3页面 ==========
            Menu(
                id = "page_3",
                title = "功能3",
                items = mutableListOf(
                    Item.Counter("jump_boost", "跳跃强化", 0),
                    Item.Counter("function3", "功能3", 0)
                )
            ),
            // ========== 功能4页面 ==========
            Menu(
                id = "page_4",
                title = "功能4",
                items = mutableListOf(
                    Item.Counter("function4_1", "功能4-1", 0),
                    Item.Counter("function4_2", "功能4-2", 0),
                    Item.Switch("function4_switch", "功能4开关", false)
                )
            )
        )
    }
}