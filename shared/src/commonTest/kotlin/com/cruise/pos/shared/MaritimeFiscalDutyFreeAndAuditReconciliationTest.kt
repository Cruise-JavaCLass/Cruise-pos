package com.cruise.pos.shared

import com.cruise.pos.shared.domain.model.fiscal.CashDrawerReconciliation
import com.cruise.pos.shared.domain.model.fiscal.FiscalItemCategory
import com.cruise.pos.shared.domain.model.fiscal.MaritimeTaxItem
import com.cruise.pos.shared.domain.model.fiscal.MaritimeTerritorialWaters
import com.cruise.pos.shared.domain.model.fiscal.PaymentTenderSummary
import com.cruise.pos.shared.domain.model.fiscal.ReceiptAuditEntry
import com.cruise.pos.shared.domain.model.fiscal.DigitalCertificateSigner
import com.cruise.pos.shared.domain.model.fiscal.FiscalAuditApiClient
import com.cruise.pos.shared.domain.model.fiscal.FiscalReportSubmissionResponse
import com.cruise.pos.shared.domain.model.fiscal.SignedFiscalReportSubmission
import com.cruise.pos.shared.domain.service.fiscal.FiscalAuditSubmissionService
import com.cruise.pos.shared.domain.service.fiscal.FiscalShiftReportBuilder
import com.cruise.pos.shared.domain.service.fiscal.MaritimeDutyFreeTaxCalculator
import com.cruise.pos.shared.domain.service.fiscal.MidDayXReportGenerator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MaritimeFiscalDutyFreeAndAuditReconciliationTest {

    @Test
    fun testInternationalWatersZeroRatedDutyFreeExemption() {
        val calculator = MaritimeDutyFreeTaxCalculator()
        val items = listOf(
            MaritimeTaxItem("ITM-01", "Glenfiddich 18yr Single Malt", FiscalItemCategory.ALCOHOL, 2, 85.0),
            MaritimeTaxItem("ITM-02", "Cuban Cigars Cohiba Box", FiscalItemCategory.TOBACCO, 1, 120.0),
            MaritimeTaxItem("ITM-03", "Rolex Datejust 41mm", FiscalItemCategory.LUXURY_GOODS, 1, 9500.0)
        )

        // Vessel is 25 nautical miles out in international waters
        val waters = calculator.determineTerritorialStatus(nauticalMilesFromCoast = 25.0, isDockedAtPort = false)
        assertEquals(MaritimeTerritorialWaters.INTERNATIONAL_WATERS, waters)

        val summary = calculator.calculateFiscalSummary(items, waters)

        // Total gross: 2*85 + 120 + 9500 = 170 + 120 + 9500 = 9790.0
        assertEquals(9790.0, summary.totalGrossSales)
        assertEquals(9790.0, summary.totalExemptDutyFreeSales)
        assertEquals(0.0, summary.totalTaxableSales)
        assertEquals(0.0, summary.totalTaxCollected)
        assertEquals(9790.0, summary.netPayableAmount)
    }

    @Test
    fun testInPortFullStandardTaxApplication() {
        val calculator = MaritimeDutyFreeTaxCalculator()
        val items = listOf(
            MaritimeTaxItem("ITM-01", "Draft Beer Pint", FiscalItemCategory.ALCOHOL, 2, 10.0), // 20% = 4.0
            MaritimeTaxItem("ITM-02", "Gourmet Burger", FiscalItemCategory.FOOD_AND_BEVERAGE, 2, 25.0) // 8% = 4.0
        )

        val waters = calculator.determineTerritorialStatus(nauticalMilesFromCoast = 0.0, isDockedAtPort = true)
        assertEquals(MaritimeTerritorialWaters.IN_PORT, waters)

        val summary = calculator.calculateFiscalSummary(items, waters, portName = "Port of Miami", countryCode = "US")

        assertEquals(70.0, summary.totalGrossSales)
        assertEquals(0.0, summary.totalExemptDutyFreeSales)
        assertEquals(70.0, summary.totalTaxableSales)
        assertEquals(8.0, summary.totalTaxCollected) // 4.0 (Alcohol) + 4.0 (Food)
        assertEquals(78.0, summary.netPayableAmount)
    }

    @Test
    fun testTerritorialWatersPartialDutyFreeQuota() {
        val calculator = MaritimeDutyFreeTaxCalculator()
        val items = listOf(
            MaritimeTaxItem("ITM-01", "Fragrance Perfume", FiscalItemCategory.LUXURY_GOODS, 1, 100.0)
        )

        // Vessel is within 8 NM of coast
        val waters = calculator.determineTerritorialStatus(nauticalMilesFromCoast = 8.0, isDockedAtPort = false)
        assertEquals(MaritimeTerritorialWaters.TERRITORIAL_WATERS, waters)

        val summary = calculator.calculateFiscalSummary(items, waters)

        assertEquals(100.0, summary.totalGrossSales)
        assertEquals(50.0, summary.totalExemptDutyFreeSales) // 50% exemption
        assertEquals(50.0, summary.totalTaxableSales)
        // Standard rate for LUXURY_GOODS is 15%, reduced is 7.5% -> 50.0 * 7.5% = 3.75
        assertEquals(3.75, summary.totalTaxCollected)
        assertEquals(103.75, summary.netPayableAmount)
    }

    @Test
    fun testMidDayXReportNonDestructiveBalanceReconciliation() {
        val generator = MidDayXReportGenerator()

        val cashDrawer = CashDrawerReconciliation(
            openingFloat = 200.0,
            cashSales = 350.0,
            paidIns = 0.0,
            paidOuts = 0.0,
            cashDrops = 100.0
        )
        assertEquals(450.0, cashDrawer.expectedCash)

        val tenders = listOf(
            PaymentTenderSummary("CASH", 12, 350.0),
            PaymentTenderSummary("CABIN_FOLIO", 25, 1200.0),
            PaymentTenderSummary("CREDIT_CARD", 10, 580.0)
        )

        val snapshot = generator.generateSnapshot(
            shiftId = "SHIFT-20261022-01",
            terminalId = "POS-BAR-04",
            outletId = "OUT-SKY-LOUNGE",
            outletName = "Skyline Sunset Lounge",
            cashierId = "STAFF-881",
            cashierName = "Alex Rivera",
            supervisorId = "SUP-102",
            supervisorName = "Elena Vance",
            openedAtEpochMs = 1774260000000L,
            snapshotTimestampEpochMs = 1774274400000L,
            totalTransactions = 47,
            totalGrossRevenue = 2130.0,
            totalDiscounts = 50.0,
            totalRefunds = 0.0,
            paymentTenders = tenders,
            cashDrawer = cashDrawer
        )

        assertFalse(snapshot.isShiftClosed, "X-Report must be strictly non-destructive (isShiftClosed = false)")
        assertEquals(2080.0, snapshot.totalNetRevenue)
        assertEquals(450.0, snapshot.cashDrawer.expectedCash)

        val slipText = generator.formatSlipText(snapshot)
        assertTrue(slipText.contains("MID-DAY AUDIT X-REPORT"))
        assertTrue(slipText.contains("NON-DESTRUCTIVE SNAPSHOT"))
        assertTrue(slipText.contains("ACTIVE / OPEN"))
        assertTrue(slipText.contains("Skyline Sunset Lounge"))
        assertTrue(slipText.contains("$2080.00"))
        assertTrue(slipText.contains("Supervisor Signature"))
    }

    @Test
    fun testFiscalReportExportAndCryptographicSigning() = runTest {
        val reportBuilder = FiscalShiftReportBuilder()

        val receipts = listOf(
            ReceiptAuditEntry(
                sequenceNumber = 1L,
                transactionId = "TX-1001",
                guestFolioId = "FOLIO-7712",
                cabinNumber = "7712",
                timestampEpochMs = 1774261000000L,
                totalAmount = 65.0,
                taxAmount = 5.2,
                tenderType = "CASH",
                signatureHashSha256 = "sig_hash_1",
                receiptPayloadHashSha256 = "payload_hash_1"
            ),
            ReceiptAuditEntry(
                sequenceNumber = 2L,
                transactionId = "TX-1002",
                guestFolioId = "FOLIO-9011",
                cabinNumber = "9011",
                timestampEpochMs = 1774262000000L,
                totalAmount = 140.0,
                taxAmount = 0.0,
                tenderType = "CABIN_FOLIO",
                signatureHashSha256 = "sig_hash_2",
                receiptPayloadHashSha256 = "payload_hash_2"
            )
        )

        val ledger = reportBuilder.assembleShiftLedger(
            exportId = "EXP-FISCAL-20261022-01",
            shiftId = "SHIFT-01",
            terminalId = "TERM-01",
            outletId = "OUT-01",
            cashierId = "STAFF-01",
            openedAtEpochMs = 1774260000000L,
            closedAtEpochMs = 1774290000000L,
            initialCashFloat = 200.0,
            receiptEntries = receipts
        )

        assertEquals(205.0, ledger.grossTotalSales)
        assertEquals(65.0, ledger.totalCashSales)
        assertEquals(140.0, ledger.totalFolioSales)
        assertNotNull(ledger.cumulativeMerkleRootSha256)
        assertEquals(64, ledger.cumulativeMerkleRootSha256.length)
        assertTrue(ledger.cumulativeMerkleRootSha256.all { it in "0123456789abcdef" })

        // Test JSON & CSV export
        val jsonOutput = reportBuilder.exportToJson(ledger)
        assertTrue(jsonOutput.contains("EXP-FISCAL-20261022-01"))
        assertTrue(jsonOutput.contains("cumulativeMerkleRootSha256"))

        val csvOutput = reportBuilder.exportToCsv(ledger)
        assertTrue(csvOutput.contains("TX-1001"))
        assertTrue(csvOutput.contains("TX-1002"))
        assertTrue(csvOutput.contains("FOLIO-7712"))

        // Mock Signer and API client
        var capturedPayloadBytes: ByteArray? = null
        val mockSigner = object : DigitalCertificateSigner {
            override suspend fun signPayload(keyAlias: String, payloadBytes: ByteArray): Result<String> {
                capturedPayloadBytes = payloadBytes
                return Result.success("MOCK_CERT_SIG_HEX_SHA256_RSA")
            }

            override fun getCertificateFingerprint(keyAlias: String): String {
                return "SHA256:F4:89:C1:90:3A:21"
            }
        }

        val mockApiClient = object : FiscalAuditApiClient {
            var submittedPackage: SignedFiscalReportSubmission? = null

            override suspend fun submitSignedFiscalReport(submission: SignedFiscalReportSubmission): Result<FiscalReportSubmissionResponse> {
                submittedPackage = submission
                return Result.success(
                    FiscalReportSubmissionResponse(
                        submissionId = "SUB-ACK-999",
                        status = "ACCEPTED",
                        ledgerAuditHash = submission.signatureHex,
                        ackTimestampEpochMs = 1774290500000L,
                        message = "Fiscal audit package verified and archived to shipboard accounting ledger"
                    )
                )
            }
        }

        val submissionService = FiscalAuditSubmissionService(mockSigner, mockApiClient, reportBuilder)
        val result = submissionService.signAndSubmitFiscalReport("CRUISE_POS_KEYSTORE_ALIAS", ledger)

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("ACCEPTED", response.status)
        assertEquals("SUB-ACK-999", response.submissionId)
        assertEquals("SHA256:F4:89:C1:90:3A:21", mockApiClient.submittedPackage?.digitalCertificateFingerprint)
        assertEquals(jsonOutput, mockApiClient.submittedPackage?.payloadJson)
        assertTrue(capturedPayloadBytes?.contentEquals(jsonOutput.encodeToByteArray()) == true)
    }

    @Test
    fun testTerritorialBoundaryAtTwelveMiles() {
        val calc = MaritimeDutyFreeTaxCalculator()
        val atTwelve = calc.determineTerritorialStatus(12.0, false)
        assertEquals(MaritimeTerritorialWaters.TERRITORIAL_WATERS, atTwelve)
        val beyondTwelve = calc.determineTerritorialStatus(12.001, false)
        assertEquals(MaritimeTerritorialWaters.INTERNATIONAL_WATERS, beyondTwelve)
        val inPort = calc.determineTerritorialStatus(0.0, true)
        assertEquals(MaritimeTerritorialWaters.IN_PORT, inPort)
    }

    @Test
    fun testSha256DigestKnownAnswerVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest(byteArrayOf())
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest("abc".encodeToByteArray())
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest(
                "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()
            )
        )
        assertEquals(
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest(ByteArray(55) { 0x61 })
        )
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest(ByteArray(56) { 0x61 })
        )
        assertEquals(
            "7d3e74a05d7db15bce4ad9ec0658ea98e3f06eeecf16b4c6fff2da457ddc2f34",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest(ByteArray(63) { 0x61 })
        )
        assertEquals(
            "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest(ByteArray(64) { 0x61 })
        )
        assertEquals(
            "635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0",
            com.cruise.pos.shared.domain.service.Sha256Digest.digest(ByteArray(65) { 0x61 })
        )
    }

    @Test
    fun testNonFiniteAmountsAndCentConsistencyInFiscalLedger() {
        val reportBuilder = FiscalShiftReportBuilder()

        // Test non-finite initial cash float rejection
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            reportBuilder.assembleShiftLedger(
                exportId = "EXP-1",
                shiftId = "S-1",
                terminalId = "T-1",
                outletId = "O-1",
                cashierId = "C-1",
                openedAtEpochMs = 1000L,
                closedAtEpochMs = 2000L,
                initialCashFloat = Double.NaN,
                receiptEntries = emptyList()
            )
        }

        // Test non-finite receipt amount rejection
        val nanReceipt = ReceiptAuditEntry(
            sequenceNumber = 1L,
            transactionId = "TX-NAN",
            guestFolioId = "FOLIO-1",
            cabinNumber = "101",
            timestampEpochMs = 1500L,
            totalAmount = Double.POSITIVE_INFINITY,
            taxAmount = 0.0,
            tenderType = "CASH",
            signatureHashSha256 = "sig",
            receiptPayloadHashSha256 = "payload"
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            reportBuilder.assembleShiftLedger(
                exportId = "EXP-2",
                shiftId = "S-2",
                terminalId = "T-2",
                outletId = "O-2",
                cashierId = "C-2",
                openedAtEpochMs = 1000L,
                closedAtEpochMs = 2000L,
                initialCashFloat = 100.0,
                receiptEntries = listOf(nanReceipt)
            )
        }

        // Test fractional cent normalization consistency
        val r1 = ReceiptAuditEntry(
            sequenceNumber = 1L,
            transactionId = "TX-FRACT-1",
            guestFolioId = "FOLIO-1",
            cabinNumber = "101",
            timestampEpochMs = 1500L,
            totalAmount = 0.125, // rounds to 0.13 or 0.12
            taxAmount = 0.012,
            tenderType = "CASH",
            signatureHashSha256 = "sig1",
            receiptPayloadHashSha256 = "payload1"
        )
        val r2 = ReceiptAuditEntry(
            sequenceNumber = 2L,
            transactionId = "TX-FRACT-2",
            guestFolioId = "FOLIO-2",
            cabinNumber = "102",
            timestampEpochMs = 1600L,
            totalAmount = 0.125,
            taxAmount = 0.012,
            tenderType = "CASH",
            signatureHashSha256 = "sig2",
            receiptPayloadHashSha256 = "payload2"
        )
        val ledger = reportBuilder.assembleShiftLedger(
            exportId = "EXP-3",
            shiftId = "S-3",
            terminalId = "T-3",
            outletId = "O-3",
            cashierId = "C-3",
            openedAtEpochMs = 1000L,
            closedAtEpochMs = 2000L,
            initialCashFloat = 50.0,
            receiptEntries = listOf(r1, r2)
        )
        // Ensure sum of receiptEntries matches grossTotalSales exactly
        val entrySum = ledger.receiptEntries.sumOf { it.totalAmount }
        assertEquals(ledger.grossTotalSales, entrySum)
    }
}
