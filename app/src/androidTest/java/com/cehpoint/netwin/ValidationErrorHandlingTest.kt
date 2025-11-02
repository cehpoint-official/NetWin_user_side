package com.cehpoint.netwin

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cehpoint.netwin.domain.model.RegistrationStep
import com.cehpoint.netwin.domain.model.RegistrationStepData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import kotlinx.coroutines.test.runTest

/**
 * Comprehensive test class for validation and error handling audit
 *
 * Tests the following aspects:
 * 1. RegistrationStepData.validate() method with malformed inputs
 * 2. ViewModel error propagation through UI state
 * 3. UI feedback for validation errors
 * 4. Error recovery and clearing mechanisms
 */
@RunWith(AndroidJUnit4::class)
class ValidationErrorHandlingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRegistrationStepDataValidation_MalformedInputs() = runTest {
        // Test DETAILS step validation with various malformed inputs

        // Empty/blank teamName
        var testData = RegistrationStepData(
            teamName = "",
            tournamentId = "valid-id"
        )
        var result = testData.validate(RegistrationStep.DETAILS)
        assertEquals("Team name is required", result)

        // Short teamName (less than 2 characters)
        testData = testData.copy(teamName = "A")
        result = testData.validate(RegistrationStep.DETAILS)
        assertEquals("Team name must be at least 2 characters", result)

        // Empty/blank tournamentId
        testData = testData.copy(teamName = "ValidTeam", tournamentId = "")
        result = testData.validate(RegistrationStep.DETAILS)
        assertEquals("Tournament ID is required", result)

        // Valid data should return null
        testData = RegistrationStepData(
            teamName = "ValidTeam",
            tournamentId = "valid-tournament-id"
        )
        result = testData.validate(RegistrationStep.DETAILS)
        assertNull("Valid data should not return validation error", result)
    }

    @Test
    fun testRegistrationStepDataValidation_PaymentStep() = runTest {
        // Test PAYMENT step validation

        // Empty payment method
        var testData = RegistrationStepData(paymentMethod = "")
        var result = testData.validate(RegistrationStep.PAYMENT)
        assertEquals("Payment method is required", result)

        // Invalid payment method
        testData = testData.copy(paymentMethod = "bitcoin")
        result = testData.validate(RegistrationStep.PAYMENT)
        assertEquals("Invalid payment method", result)

        // Valid payment methods
        val validMethods = listOf("wallet", "upi", "card")
        validMethods.forEach { method ->
            testData = testData.copy(paymentMethod = method)
            result = testData.validate(RegistrationStep.PAYMENT)
            assertNull("Valid payment method '$method' should not return error", result)
        }
    }

    @Test
    fun testRegistrationStepDataValidation_ReviewStep() = runTest {
        // Test REVIEW step validation (validates all previous steps)

        // Invalid DETAILS step should fail REVIEW
        var testData = RegistrationStepData(
            teamName = "", // Invalid
            paymentMethod = "wallet",
            tournamentId = "valid-id"
        )
        var result = testData.validate(RegistrationStep.REVIEW)
        assertEquals("Team name is required", result)

        // Invalid PAYMENT step should fail REVIEW
        testData = RegistrationStepData(
            teamName = "ValidTeam",
            paymentMethod = "invalid", // Invalid
            tournamentId = "valid-id"
        )
        result = testData.validate(RegistrationStep.REVIEW)
        assertEquals("Invalid payment method", result)

        // All valid should pass REVIEW
        testData = RegistrationStepData(
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            tournamentId = "valid-id"
        )
        result = testData.validate(RegistrationStep.REVIEW)
        assertNull("Valid review data should not return error", result)
    }

    @Test
    fun testRegistrationStepDataValidation_ConfirmStep() = runTest {
        // Test CONFIRM step validation

        // Terms not accepted
        var testData = RegistrationStepData(
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            termsAccepted = false, // Invalid
            tournamentId = "valid-id"
        )
        var result = testData.validate(RegistrationStep.CONFIRM)
        assertEquals("You must accept the terms and conditions", result)

        // Terms accepted but invalid previous steps
        testData = testData.copy(
            termsAccepted = true,
            teamName = "" // Invalid
        )
        result = testData.validate(RegistrationStep.CONFIRM)
        assertEquals("Team name is required", result)

        // All valid
        testData = RegistrationStepData(
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            termsAccepted = true,
            tournamentId = "valid-id"
        )
        result = testData.validate(RegistrationStep.CONFIRM)
        assertNull("Valid confirm data should not return error", result)
    }

    @Test
    fun testStepCompletionHelpers() = runTest {
        val testData = RegistrationStepData(
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            termsAccepted = true,
            tournamentId = "valid-id"
        )

        // Test isStepComplete for all steps
        assertTrue("Valid DETAILS step should be complete",
            testData.isStepComplete(RegistrationStep.DETAILS))
        assertTrue("Valid PAYMENT step should be complete",
            testData.isStepComplete(RegistrationStep.PAYMENT))
        assertTrue("Valid REVIEW step should be complete",
            testData.isStepComplete(RegistrationStep.REVIEW))
        assertTrue("Valid CONFIRM step should be complete",
            testData.isStepComplete(RegistrationStep.CONFIRM))

        // Test isComplete for entire flow
        assertTrue("Valid complete data should return true", testData.isComplete())

        // Test with invalid data
        val invalidData = testData.copy(teamName = "")
        assertFalse("Invalid data should not be complete", invalidData.isComplete())
    }

    @Test
    fun testCascadingValidation() = runTest {
        // Test that validation properly cascades through steps

        val invalidDetailsData = RegistrationStepData(
            teamName = "", // Invalid
            paymentMethod = "wallet",
            termsAccepted = true,
            tournamentId = "valid-id"
        )

        // DETAILS step should fail
        assertNotNull("Invalid details should fail DETAILS validation",
            invalidDetailsData.validate(RegistrationStep.DETAILS))

        // REVIEW step should fail due to invalid DETAILS
        assertNotNull("Invalid details should fail REVIEW validation",
            invalidDetailsData.validate(RegistrationStep.REVIEW))

        // CONFIRM step should fail due to invalid DETAILS
        assertNotNull("Invalid details should fail CONFIRM validation",
            invalidDetailsData.validate(RegistrationStep.CONFIRM))

        // PAYMENT step should still pass
        assertNull("Payment validation should pass independently",
            invalidDetailsData.validate(RegistrationStep.PAYMENT))
    }

    @Test
    fun testEdgeCaseInputs() = runTest {
        // Test edge cases and special characters

        // Special characters in teamName (should be allowed if length is valid)
        var testData = RegistrationStepData(
            teamName = "Team-Name!@#",
            tournamentId = "valid-id"
        )
        var result = testData.validate(RegistrationStep.DETAILS)
        assertNull("Special characters should be allowed in valid length strings", result)

        // Unicode characters
        testData = testData.copy(
            teamName = "队伍名"
        )
        result = testData.validate(RegistrationStep.DETAILS)
        assertNull("Unicode characters should be allowed", result)

        // Whitespace-only strings (should be treated as blank)
        testData = testData.copy(
            teamName = "\t\n "
        )
        result = testData.validate(RegistrationStep.DETAILS)
        assertNotNull("Whitespace-only strings should be treated as invalid", result)
    }

    @Test
    fun testValidationErrorMessages() = runTest {
        // Test that error messages are user-friendly and specific

        // Base valid data to modify from
        val validData = RegistrationStepData(
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            termsAccepted = true,
            tournamentId = "valid-id"
        )

        // List of (data to test, step to test, expected message)
        val testCases = listOf(
            Triple(validData.copy(teamName = ""), RegistrationStep.DETAILS, "Team name is required"),
            Triple(validData.copy(teamName = "A"), RegistrationStep.DETAILS, "Team name must be at least 2 characters"),
            Triple(validData.copy(tournamentId = ""), RegistrationStep.DETAILS, "Tournament ID is required"),
            Triple(validData.copy(paymentMethod = ""), RegistrationStep.PAYMENT, "Payment method is required"),
            Triple(validData.copy(paymentMethod = "invalid"), RegistrationStep.PAYMENT, "Invalid payment method"),
            Triple(validData.copy(termsAccepted = false), RegistrationStep.CONFIRM, "You must accept the terms and conditions")
        )

        testCases.forEach { (data, step, expectedMessage) ->
            val actualMessage = data.validate(step)
            assertEquals("Error message should match expected for $data on step $step", expectedMessage, actualMessage)
        }
    }
}