package com.storm.coldwind.floatwindow

sealed class Item {
    object Divider : Item()

    data class Switch(
        val title: String,
        var enabled: Boolean,
        val onToggle: (Boolean) -> Unit
    ) : Item()

    data class Counter(
        val title: String,
        var value: Int,
        val onChange: (Int) -> Unit
    ) : Item()

    data class Selector(
        val title: String,
        val options: List<String>,
        var selected: String,
        val onSelect: (String) -> Unit
    ) : Item()
}

data class Menu(
    val id: String,
    val title: String,
    val items: MutableList<Item>,
    val onClick: (() -> Unit)? = null
)