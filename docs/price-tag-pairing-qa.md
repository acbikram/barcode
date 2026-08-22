# Price Tag PC Pairing Manual QA Checklist

## Preconditions

Install the current Barcode To CSV build and ensure the Price Tag PC application displays a fresh v3 pairing QR code. Test on the same Wi-Fi network. Use a modern arm64-v8a device when available.

## Pairing Flow

| Step | Expected result |
|---|---|
| Open **Settings → Update product catalog → Pair with Price Tag PC**. | The screen shows **Not paired** and a scan action. No camera prompt appears before the scan action is selected. |
| Select the pairing action. | The app requests camera permission only now and opens the QR scanner when permission is granted. |
| Scan a valid, unexpired v3 Price Tag PC QR code. | A confirmation dialog shows the PC name, host, and port. No pairing occurs yet. |
| Tap **Confirm**. | The app shows **Pairing**, then **Paired with [PC name]** after the PC replies successfully. |
| Scan an expired, malformed, wrong-version, or wrong-type QR code. | The app shows an actionable generic failure; no existing paired PC is replaced. |
| Cancel the confirmation dialog. | The app returns to QR scanning and does not send a pairing request. |

## Authenticated Connection Flow

| Step | Expected result |
|---|---|
| From the paired-PC screen, tap **Test Connection**. | The app shows **Testing**, then returns to the paired status on success. The PC receives `PTAGAUTH` followed by `PTAGPNG1`. |
| Send a CSV through the existing Share WiFi workflow. | The paired PC host and port are used. The PC receives `PTAGAUTH` followed by the unchanged `PTAGCSV1`, 4-byte CSV length, and CSV payload framing. |
| Pull the product catalog through Settings. | The paired PC is used and the `PTAGAUTH` preamble precedes the existing catalog request. |
| Reject the authenticated request on the PC. | The app reports a generic connection failure and does not silently retry using legacy framing. |

## Legacy and Forgetting Flow

| Step | Expected result |
|---|---|
| On an unpaired installation, use the existing manual PC host and port flow. | Discovery, test, and CSV transfer retain their prior legacy framing without `PTAGAUTH`. |
| On the paired-PC screen, tap **Forget this PC**. | The encrypted paired-PC token and record are deleted; the state returns to **Not paired**. |
| After forgetting, use the export workflow. | The legacy manual connection path remains available; pairing again requires scanning a new QR code. |

## Security Checks

Confirm that logs, UI errors, backups, and exported files do not contain the QR one-time code, device token, or raw socket frames. Confirm that the encrypted pairing preference file is excluded from cloud backup and device transfer.
