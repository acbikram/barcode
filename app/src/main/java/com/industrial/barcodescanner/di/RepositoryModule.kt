package com.industrial.barcodescanner.di

import com.industrial.barcodescanner.data.repository.ScannedItemRepositoryImpl
import com.industrial.barcodescanner.data.repository.ProductCatalogRepositoryImpl
import com.industrial.barcodescanner.domain.repository.ScannedItemRepository
import com.industrial.barcodescanner.domain.repository.ProductCatalogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScannedItemRepository(impl: ScannedItemRepositoryImpl): ScannedItemRepository

    @Binds
    @Singleton
    abstract fun bindProductCatalogRepository(impl: ProductCatalogRepositoryImpl): ProductCatalogRepository
}
