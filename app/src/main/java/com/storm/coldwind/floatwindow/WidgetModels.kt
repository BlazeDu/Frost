package com.storm.coldwind.floatwindow

sealed class Item {
    data class Switch(
        val id: String,
        val title: String,
        var enabled: Boolean
    ) : Item()

    data class Counter(
        val id: String,
        val title: String,
        var value: Int
    ) : Item()

    data class Selector(
        val id: String,
        val title: String,
        val options: List<String>,
        var selected: String
    ) : Item()
}

data class Menu(
    val id: String,
    val title: String,
    val items: MutableList<Item>
)