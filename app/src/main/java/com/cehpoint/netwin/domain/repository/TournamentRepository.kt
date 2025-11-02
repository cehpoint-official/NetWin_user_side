package com.cehpoint.netwin.domain.repository

import com.cehpoint.netwin.domain.model.Tournament
import com.cehpoint.netwin.domain.model.TournamentStatus
import kotlinx.coroutines.flow.Flow


interface TournamentRepository {
    suspend fun getFeaturedTournaments(): List<Tournament>

    // Real-time stream of all tournaments
    fun getTournaments(): Flow<List<Tournament>>

    suspend fun getTournamentById(id: String): Tournament?
    suspend fun createTournament(tournament: Tournament): Result<Tournament>
    suspend fun updateTournament(tournament: Tournament): Result<Tournament>

    /**
     * Fetches the list of tournament IDs that the user has registered for in real-time.
     * **Updated to return a Flow for reactive UI/ViewModel logic.**
     * @param userId The ID of the user
     * @return Flow of tournament IDs that the user has registered for
     */
    fun getUserTournamentRegistrations(userId: String): Flow<List<String>> // <<< UPDATED
    suspend fun deleteTournament(id: String): Result<Unit>
    suspend fun joinTournament(tournamentId: String, userId: String): Result<Unit>
    suspend fun leaveTournament(tournamentId: String, userId: String): Result<Unit>
    suspend fun updateTournamentStatus(id: String, status: TournamentStatus): Result<Unit>
    suspend fun registerForTournament(
        tournamentId: String,
        userId: String,
        displayName: String,
        teamName: String,
        playerIds: List<String>
    ): Result<Unit>
    suspend fun isUserRegisteredForTournament(tournamentId: String, userId: String): Boolean
}