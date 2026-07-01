package dev.shallowdusty.oplusotastudio.core.storage

import android.content.Context
import androidx.room.Room

private const val DATABASE_NAME = "ota-studio.db"

fun createOtaStudioDatabase(context: Context): OtaStudioDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        OtaStudioDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
