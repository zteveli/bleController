package com.example.blecontroller.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Embedded

object ControlType {
    const val BUTTON = "button"
    const val SLIDER_HORIZONTAL = "slider_horizontal"
    const val SLIDER_VERTICAL = "slider_vertical"
}

@Entity(tableName = "layouts")
data class LayoutPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val boundDeviceAddress: String? = null,
    val boundDeviceName: String? = null,
    val isLocked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "buttons",
    foreignKeys = [
        ForeignKey(
            entity = LayoutPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["layoutId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("layoutId")],
)
data class VirtualButtonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val layoutId: Long,
    val label: String,
    val controlType: String = ControlType.BUTTON,
    val serviceUuid: String = "",
    val characteristicUuid: String = "",
    val payloadHex: String = "01",
    val sliderMin: Float = 0f,
    val sliderMax: Float = 100f,
    val sliderValue: Float = 0f,
    val sliderPrefix: String = "",
    val xFraction: Float = 0.12f,
    val yFraction: Float = 0.12f,
    val widthFraction: Float = 0.26f,
    val heightFraction: Float = 0.12f,
    val sortOrder: Int = 0,
)

data class LayoutWithButtons(
    @Embedded val layout: LayoutPresetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "layoutId",
        entity = VirtualButtonEntity::class,
    )
    val buttons: List<VirtualButtonEntity>,
)
