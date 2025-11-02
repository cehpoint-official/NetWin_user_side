
package com.cehpoint.netwin.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Represents a registration for a tournament.
 * Each registration can have multiple team members.
 */
data class TournamentRegistration(
    @DocumentId
    val id: String = "",
    val tournamentId: String = "",
    val userId: String = "",
    val teamName: String = "",
    val teamMembers: List<TeamMember> = emptyList(),
    val paymentStatus: String = "pending",
    val registeredAt: Timestamp = Timestamp.now()
) {
    companion object {
        /**
         * Factory function to create a TournamentRegistration from Firestore data.
         */
        fun fromFirestore(
            id: String,
            tournamentId: String,
            userId: String,
            teamName: String,
            teamMembers: List<Map<String, String>>? = null,
            paymentStatus: String = "pending",
            registeredAt: Timestamp
        ): TournamentRegistration {
            val members = teamMembers?.map { TeamMember.fromMap(it) } ?: emptyList()
            return TournamentRegistration(
                id = id,
                tournamentId = tournamentId,
                userId = userId,
                teamName = teamName,
                teamMembers = members,
                paymentStatus = paymentStatus,
                registeredAt = registeredAt
            )
        }
    }

    /**
     * Converts this TournamentRegistration to a Firestore-compatible map.
     */
    fun toFirestore(): Map<String, Any> = mapOf(
        "tournamentId" to tournamentId,
        "userId" to userId,
        "teamName" to teamName,
        "teamMembers" to teamMembers.map { it.toMap() },
        "paymentStatus" to paymentStatus,
        "registeredAt" to registeredAt
    )
}

/**
 * Represents a single member of a team.
 */
data class TeamMember(
    val username: String = "",
    val inGameId: String = ""
) {
    /**
     * Converts TeamMember to a map for Firestore.
     */
    fun toMap(): Map<String, String> = mapOf(
        "username" to username,
        "inGameId" to inGameId
    )

    companion object {
        /**
         * Creates a TeamMember from a Firestore map.
         */
        fun fromMap(map: Map<String, String>): TeamMember = TeamMember(
            username = map["username"] ?: "",
            inGameId = map["inGameId"] ?: ""
        )
    }
}
