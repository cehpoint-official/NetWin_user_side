package com.cehpoint.netwin.utils

import com.cehpoint.netwin.data.model.KycStatus
import kotlinx.coroutines.flow.Flow

// Since this component monitors tournament results to trigger wallet updates,
// it should be defined as an interface, and its implementation should be provided
// via a Hilt module (which is a separate step).

interface TournamentResultMonitor {
    /**
     * Listens for tournament results for a given user that are pending a kill prize payout.
     * Emits the kill prize amount when a new, unacknowledged result is found.
     */
    fun observeKillPrizeAmount(userId: String?): Flow<Double>

    /**
     * Acknowledges that a specific kill prize amount has been processed and credited,
     * preventing re-processing.
     */
    fun acknowledgePayout(userId: String?, amount: Double)

    /**
     * Placeholder method to show how the withdrawal limit logic might connect.
     */
    fun getWithdrawalLimit(kycStatus: KycStatus): Double {
        return when (kycStatus) {
            KycStatus.VERIFIED -> 50000.0 // Full limit
            KycStatus.PENDING -> 5000.0   // Lower limit
            KycStatus.REJECTED -> 0.0     // No limit
        }
    }
}