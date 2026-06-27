package com.industrial.barcodescanner.di

import android.content.Context
import com.industrial.barcodescanner.data.local.database.BarcodeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBarcodeDatabase(@ApplicationContext context: Context): BarcodeDatabase {
        return BarcodeDatabase.getInstance(context)
    }
}
