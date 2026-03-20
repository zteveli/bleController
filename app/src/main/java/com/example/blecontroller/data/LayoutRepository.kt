package com.example.blecontroller.data

import kotlinx.coroutines.flow.Flow

class LayoutRepository(
    private val dao: LayoutDao,
) {
    fun observeLayouts(): Flow<List<LayoutWithButtons>> = dao.observeLayouts()

    suspend fun ensureDefaultLayout() {
        if (dao.layoutCount() == 0) {
            dao.insertLayout(LayoutPresetEntity(name = "Alap layout"))
        }
    }

    suspend fun createLayout(name: String): Long {
        return dao.insertLayout(
            LayoutPresetEntity(
                name = name,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateLayout(entity: LayoutPresetEntity) {
        dao.updateLayout(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteLayout(layout: LayoutPresetEntity) {
        dao.deleteLayout(layout)
    }

    suspend fun addButton(layoutId: Long, nextSortOrder: Int): Long {
        return dao.insertButton(
            VirtualButtonEntity(
                layoutId = layoutId,
                label = "Gomb ${nextSortOrder + 1}",
                sortOrder = nextSortOrder,
            )
        )
    }

    suspend fun updateButton(button: VirtualButtonEntity) {
        dao.updateButton(button)
    }

    suspend fun deleteButton(button: VirtualButtonEntity) {
        dao.deleteButton(button)
    }

    suspend fun duplicateLayout(source: LayoutWithButtons, newName: String): Long {
        val newId = dao.insertLayout(
            source.layout.copy(
                id = 0,
                name = newName,
                updatedAt = System.currentTimeMillis(),
            )
        )
        dao.insertButtons(
            source.buttons.map {
                it.copy(id = 0, layoutId = newId)
            }
        )
        return newId
    }
}
