package com.cehpoint.netwin.di

import com.cehpoint.netwin.utils.TournamentResultMonitor
import com.cehpoint.netwin.utils.TournamentResultMonitorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UtilsModule {

    /**
     * @Binds is used to tell Hilt/Dagger what implementation (concrete class)
     * to use when an interface (abstract type) is requested.
     * * Fixes the Dagger/MissingBinding error for TournamentResultMonitor.
     */
    @Binds
    @Singleton
    abstract fun bindTournamentResultMonitor(
        monitorImpl: TournamentResultMonitorImpl // The concrete implementation
    ): TournamentResultMonitor // The abstract interface
}