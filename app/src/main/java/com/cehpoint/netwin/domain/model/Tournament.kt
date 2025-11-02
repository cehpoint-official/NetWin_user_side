package com.cehpoint.netwin.domain.model

import android.util.Log
import java.util.Date

// Data class representing a Tournament, aligned with Firestore structure
data class Tournament(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val gameType: String = "",
    val matchType: String = "",
    val map: String = "",
    val startTime: Long = 0, // Unix timestamp in milliseconds
    val entryFee: Double = 0.0,
    val prizePool: Double = 0.0,
    val maxTeams: Int = 0,
    val registeredTeams: Int = 0,
    val status: String = "upcoming", // Raw status string from Firestore
    val rules: List<String> = emptyList(),
    val bannerImage: String? = null,
    val rewardsDistribution: List<RewardDistribution> = emptyList(),
    val createdAt: Long = 0,
    val killReward: Double? = null,
    val roomId: String? = null,
    val roomPassword: String? = null,
    val actualStartTime: Long? = null,
    val completedAt: Long? = null, // Unix timestamp in milliseconds
    val country: String? = null,
    val registrationStartTime: Long? = null, // Unix timestamp in milliseconds
    val registrationEndTime: Long? = null // Unix timestamp in milliseconds
) {
    // Calculated property for Tournament Mode enum
    val mode: TournamentMode
        get() = when (matchType.uppercase()) {
            "SOLO" -> TournamentMode.SOLO
            "DUO" -> TournamentMode.DUO
            "SQUAD" -> TournamentMode.SQUAD
            "TRIO" -> TournamentMode.TRIO
            "CUSTOM" -> TournamentMode.CUSTOM
            else -> TournamentMode.SQUAD // Default if matchType is unrecognized
        }

    // Calculated property for Team Size based on mode
    val teamSize: Int
        get() = when (mode) {
            TournamentMode.SOLO -> 1
            TournamentMode.DUO -> 2
            TournamentMode.TRIO -> 3
            TournamentMode.SQUAD -> 4
            TournamentMode.CUSTOM -> 4 // Default for custom, adjust if needed
        }

    // Calculated property: Checks if the tournament has reached max capacity
    val isFull: Boolean
        get() = when {
            // A tournament configured with 0 or negative max teams is considered full
            maxTeams <= 0 -> {
                Log.d("TournamentModel", "$name -> isFull = true (maxTeams <= 0)")
                true
            }
            // Standard check: registered count meets or exceeds the maximum
            registeredTeams >= maxTeams -> {
                Log.d("TournamentModel", "$name -> isFull = true (${registeredTeams}/${maxTeams})")
                true
            }
            // Otherwise, there are available slots
            else -> {
                Log.d("TournamentModel", "$name -> isFull = false (${registeredTeams}/${maxTeams})")
                false
            }
        }

    // Calculated property: Checks if the current time is within the registration window
    val isRegistrationWindowOpen: Boolean
        get() {
            val now = System.currentTimeMillis()
            // Use 0L (past) if registrationStartTime is null
            val regStart = registrationStartTime ?: 0L
            // Use tournament startTime if registrationEndTime is null
            // If startTime is also 0, regEnd becomes 0, correctly closing the window
            val regEnd = registrationEndTime ?: startTime

            // If the effective end time is invalid (0 or past), the window cannot be open
            if (regEnd <= 0) {
                Log.d("TournamentModel", "$name -> isRegistrationWindowOpen = false (regEnd <= 0: $regEnd)")
                return false
            }

            // Check if current time is between start and end times
            val isOpen = now >= regStart && now < regEnd
            Log.d("TournamentModel", "$name -> isRegistrationWindowOpen = $isOpen (now: $now, start: $regStart, end: $regEnd)")
            return isOpen
        }

    // Calculated property: Determines the current status based on time and Firestore data
    val computedStatus: TournamentStatus
        get() {
            val now = System.currentTimeMillis()

            // 0. Handle Invalid Start Time (Most definitive "not active" state)
            if (startTime <= 0) {
                Log.d("TournamentStatus", "$name -> Computed: COMPLETED (Invalid startTime: $startTime)")
                return TournamentStatus.COMPLETED
            }

            // 1. Check Explicit Completed Status (from Firestore or time)
            if (status.equals("COMPLETED", ignoreCase = true)) {
                Log.d("TournamentStatus", "$name -> Computed: COMPLETED (from status field)")
                return TournamentStatus.COMPLETED
            }
            if (completedAt != null && now >= completedAt) {
                Log.d("TournamentStatus", "$name -> Computed: COMPLETED (from completedAt time)")
                return TournamentStatus.COMPLETED
            }

            // 2. Check Explicit Live/Ongoing Status (from Firestore)
            if (status.equals("LIVE", ignoreCase = true) || status.equals("ONGOING", ignoreCase = true)) {
                val currentStatus = if (roomId != null) TournamentStatus.ROOM_OPEN else TournamentStatus.ONGOING
                Log.d("TournamentStatus", "$name -> Computed: $currentStatus (from status field)")
                return currentStatus
            }
            if (status.equals("ROOM_OPEN", ignoreCase = true)) {
                Log.d("TournamentStatus", "$name -> Computed: ROOM_OPEN (from status field)")
                return TournamentStatus.ROOM_OPEN
            }

            // 3. Time-based Logic (if status field wasn't definitive)
            return when {
                // UPCOMING: More than 10 minutes before start
                now < startTime - 10 * 60 * 1000 -> {
                    Log.d("TournamentStatus", "$name -> Computed: UPCOMING (Time-based)")
                    TournamentStatus.UPCOMING
                }
                // STARTS_SOON: Within 10 minutes of start
                now < startTime -> {
                    Log.d("TournamentStatus", "$name -> Computed: STARTS_SOON (Time-based)")
                    TournamentStatus.STARTS_SOON
                }
                // ROOM_OPEN: Start time passed, room ID is set
                roomId != null && now >= startTime -> {
                    Log.d("TournamentStatus", "$name -> Computed: ROOM_OPEN (Time-based)")
                    TournamentStatus.ROOM_OPEN
                }
                // ONGOING: Start time passed (and not completed/room open yet)
                now >= startTime -> {
                    Log.d("TournamentStatus", "$name -> Computed: ONGOING (Time-based)")
                    TournamentStatus.ONGOING
                }
                // Fallback (shouldn't happen with startTime > 0 check)
                else -> {
                    Log.w("TournamentStatus", "$name -> Computed: Fallback to UPCOMING (Unexpected state)")
                    TournamentStatus.UPCOMING
                }
            }
        }
}

// Data class for reward distribution details
data class RewardDistribution(
    val position: Int,
    val percentage: Double // Or change to 'amount: Double' if storing fixed amounts
)

// Enum defining possible tournament statuses used within the app logic
enum class TournamentStatus {
    UPCOMING,
    STARTS_SOON,
    ROOM_OPEN,
    ONGOING,
    COMPLETED
}

// Enum defining possible tournament modes
enum class TournamentMode {
    SOLO, DUO, SQUAD, TRIO, CUSTOM
}