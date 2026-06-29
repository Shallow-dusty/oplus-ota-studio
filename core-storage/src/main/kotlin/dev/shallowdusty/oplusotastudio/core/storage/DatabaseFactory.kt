package dev.shallowdusty.oplusotastudio.core.storage

import android.content.Context
import androidx.room.Room

private const val DATABASE_NAME = "ota-studio.db"

fun createOtaStudioDatabase(context: Context): OtaStudioDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        OtaStudioDatabase::class.java,
        DATABASE_NAME,
    ).build()
