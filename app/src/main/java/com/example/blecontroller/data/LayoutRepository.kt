package com.example.blecontroller.data

import kotlinx.coroutines.flow.Flow

class LayoutRepository(
    private val dao: LayoutDao,
) {
    fun observeLayouts(): Flow<List<LayoutWithButtons>> = dao.observeLayouts()

    suspend fun ensureDefaultLayout() {
        if (dao.layoutCount() == 0) {
            dao.insertLayout(LayoutPresetEntity(name = "Default layout"))
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

    suspend fun addControl(
        layoutId: Long,
        controlType: String,
        nextSortOrder: Int,
        xFraction: Float = 0.12f,
        yFraction: Float = 0.12f,
    ): Long {
        val defaultWidth = if (controlType == ControlType.SLIDER_VERTICAL) 0.14f else 0.32f
        val defaultHeight = if (controlType == ControlType.SLIDER_VERTICAL) 0.32f else 0.14f
        val defaultLabel = when (controlType) {
            ControlType.SLIDER_HORIZONTAL -> "Slider H ${nextSortOrder + 1}"
            ControlType.SLIDER_VERTICAL -> "Slider V ${nextSortOrder + 1}"
            else -> "Button ${nextSortOrder + 1}"
        }
        return dao.insertButton(
            VirtualButtonEntity(
                layoutId = layoutId,
                label = defaultLabel,
                controlType = controlType,
                xFraction = xFraction,
                yFraction = yFraction,
                widthFraction = defaultWidth,
                heightFraction = defaultHeight,
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
