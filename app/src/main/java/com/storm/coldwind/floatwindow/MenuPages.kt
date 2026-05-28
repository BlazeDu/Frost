package com.storm.coldwind.floatwindow

import com.storm.coldwind.floatwindow.ActionDispatcher.showFloatingMessage

object MenuPages {

    // 开启悬浮窗后默认选中的菜单ID
    var selectedId: String = "page_1"
        private set

    // 切换菜单的回调，由 Service 注册
    var onMenuChanged: ((Menu) -> Unit)? = null

    fun getPages(): MutableList<Menu> {
        return mutableListOf(
            Menu(
                id = "page_1",
                title = "功能1",
                items = mutableListOf(
                    Item.Switch(
                        title = "离线模式",
                        enabled = false,
                        onToggle = { enabled ->
                            showFloatingMessage("离线模式: ${if (enabled) "开启" else "关闭"}")
                        }
                    ),
                    Item.Divider,
                    Item.Switch(
                        title = "无限能量",
                        enabled = false,
                        onToggle = { enabled ->
                            showFloatingMessage("无限能量: ${if (enabled) "开启" else "关闭"}")
                        }
                    )
                ),
                onClick = { selectMenu("page_1") }
            ),
            Menu(
                id = "page_2",
                title = "功能2",
                items = mutableListOf(
                    Item.Selector(
                        title = "游戏倍速",
                        options = listOf("默认", "二倍", "四倍", "八倍"),
                        selected = "默认",
                        onSelect = { speed ->
                            showFloatingMessage("游戏倍速: $speed")
                        }
                    ),
                    Item.Counter(
                        title = "数量",
                        value = 0,
                        onChange = { count ->
                            showFloatingMessage("光翼数量: $count")
                        }
                    ),
                    Item.Counter(
                        title = "飞行强化",
                        value = 0,
                        onChange = { value ->
                            showFloatingMessage("飞行强化: $value")
                        }
                    )
                ),
                onClick = { selectMenu("page_2") }
            ),
            Menu(
                id = "page_3",
                title = "功能3",
                items = mutableListOf(
                    Item.Counter(
                        title = "跳跃强化",
                        value = 0,
                        onChange = { value ->
                            showFloatingMessage("跳跃强化: $value")
                        }
                    ),
                    Item.Counter(
                        title = "功能3",
                        value = 0,
                        onChange = { value ->
                            showFloatingMessage("功能3: $value")
                        }
                    )
                ),
                onClick = { selectMenu("page_3") }
            ),
            Menu(
                id = "page_4",
                title = "功能4",
                items = mutableListOf(
                    Item.Counter(
                        title = "功能4-1",
                        value = 0,
                        onChange = { value ->
                            showFloatingMessage("功能4-1: $value")
                        }
                    ),
                    Item.Counter(
                        title = "功能4-2",
                        value = 0,
                        onChange = { value ->
                            showFloatingMessage("功能4-2: $value")
                        }
                    ),
                    Item.Switch(
                        title = "功能4开关",
                        enabled = false,
                        onToggle = { enabled ->
                            showFloatingMessage("功能4开关: ${if (enabled) "开启" else "关闭"}")
                        }
                    )
                ),
                onClick = { selectMenu("page_4") }
            )
        )
    }

    private fun selectMenu(id: String) {
        if (selectedId == id) return
        selectedId = id
        val newMenu = getPages().find { it.id == id }
        newMenu?.let { onMenuChanged?.invoke(it) }
    }

    fun getCurrentMenu(): Menu? {
        return getPages().find { it.id == selectedId }
    }
}