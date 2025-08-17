# Content Provider Enhancement Plan (CP.md)

## 1. Requirements

The goal is to enhance the application by reading and processing all available and relevant data from the `Telephony` content provider for both SMS and MMS messages. This will enrich the data available to the app, allowing for a more complete and accurate representation of the original messages.

### 1.1. Functional Requirements

- **MMS Multimedia:** The application must ingest all non-text parts of an MMS, including images, videos, and audio clips.
- **MMS Attachments:** The application must save a local copy of these multimedia attachments for offline access and display.
- **MMS Presentation:** The application must parse SMIL (Synchronized Multimedia Integration Language) files to understand the intended layout and presentation of MMS parts.
- **MMS Recipients:** The application must read all recipient types (To, From, Cc, Bcc) associated with an MMS message.
- **SMS Metadata:** The application should be able to parse raw SMS PDU data to extract low-level metadata, specifically the User Data Header (UDH) and Data Coding Scheme (DCS).

## 2. Specifications

This plan details changes primarily within `MessageScanner.kt` and `Models.kt`.

### 2.1. Data Model Changes (`Models.kt`)

1.  **`MessagePart` Data Class:** A new data class will be created to represent individual parts of a message, distinguishing between text and attachments.

    ```kotlin
    // In Models.kt
    enum class MessagePartType { TEXT, IMAGE, VIDEO, AUDIO, VCARD, OTHER }

    data class MessagePart(
        val partId: Long,           // The part's unique ID from the provider
        val type: MessagePartType,
        val contentType: String,    // The raw MIME type
        val localUri: Uri?,         // Local URI for stored attachments
        val text: String?,          // Text content, if applicable
        val filename: String?,
        val size: Long              // Size of the part in bytes
    )
    ```

2.  **`SmsItem` Modification:** The `SmsItem` data class will be updated to include a list of `MessagePart` objects and a field for the SMIL presentation layout. It will also include a list for all addresses.

    ```kotlin
    // In Models.kt
    data class SmilLayout(
        // Defines the order and regions for parts
        val partOrder: List<Long> // List of part IDs in presentation order
    )

    data class MessageAddress(
        val address: String,
        val type: String // e.g., "From", "To", "Cc", "Bcc"
    )

    data class SmsItem(
        // ... existing fields ...
        val addresses: List<MessageAddress> = emptyList(),
        val parts: List<MessagePart> = emptyList(),
        val smilLayout: SmilLayout? = null,
        // ... existing fields ...
    )
    ```

### 2.2. Message Ingestion Logic (`MessageScanner.kt`)

1.  **MMS Part Handling:** The `resolveMmsPartsMeta` function will be enhanced. For each part, it will:
    *   Read the `_data` column to get the path to the cached file.
    *   Use a new helper function, `copyPartToLocalStorage`, to copy the attachment to the app's private storage (e.g., `mms_attachments` directory) and get a local `Uri`.
    *   Determine the `MessagePartType` based on the MIME type (`ct` column).
    *   Populate a `MessagePart` object with all relevant details.

2.  **SMIL Parsing:** A new function, `parseSmil(smilXml: String): SmilLayout`, will be created.
    *   It will use `XmlPullParser` to read the SMIL content.
    *   It will look for `<par>` (parallel) tags which define slides.
    *   Inside each `<par>`, it will find `<img>`, `<video>`, `<audio>`, and `<text>` tags and extract their `src` attribute. The `src` often corresponds to the `cid` (Content-ID) of a message part.
    *   It will return a `SmilLayout` object that defines the order in which parts should be presented.

3.  **MMS Address Handling:** The `scanMmsAddrs` function will be updated to map the integer `type` from the `addr` table to a human-readable string ("From", "To", "Cc", "Bcc") and return a list of `MessageAddress` objects.

4.  **SMS PDU Parsing (Optional Enhancement):**
    *   A new column, `pdu`, would be added to the `scanSms` projection.
    *   A new function, `parseSmsPdu(pdu: String)`, would be created to decode the hexadecimal PDU string. This is a complex, low-level task requiring careful bitwise operations according to the GSM 03.40 specification.
    *   This function would extract the UDH (if present) and the DCS, which would be stored in new fields in the `SmsItem` model. Due to its complexity, this should be considered a lower-priority enhancement.

## 3. Implementation Plan

### 3.1. Phase 1: MMS Data Model & Core Logic

1.  **Update `Models.kt`:** Implement the `MessagePart`, `MessagePartType`, `SmilLayout`, and `MessageAddress` classes. Modify `SmsItem` to include the new fields.
2.  **Implement `copyPartToLocalStorage`:** Create this helper function in `MessageScanner.kt` or a new utility file. It will take a part's content `Uri` and return a new `Uri` pointing to a file in the app's local storage.
3.  **Enhance `resolveMmsPartsMeta`:** Modify this function to create and return a list of fully populated `MessagePart` objects, including calling the new storage helper for attachments.
4.  **Enhance `scanMmsAddrs`:** Update this function to return a list of `MessageAddress` objects with mapped type strings.
5.  **Update `scanMms`:** Modify the main MMS scanning function to call the enhanced helper functions and populate the new `addresses` and `parts` fields in the `SmsItem`.

### 3.2. Phase 2: SMIL Parsing & UI Preparation

1.  **Implement `parseSmil`:** Create the SMIL parsing function using `XmlPullParser`.
2.  **Integrate SMIL Parsing:** In `resolveMmsPartsMeta`, when an `application/smil` part is found, read its text content and pass it to `parseSmil`. Store the resulting `SmilLayout` object in the `SmsItem`.

## 4. Test Cases

### 4.1. Unit Tests

-   **`copyPartToLocalStorage`:**
    -   Verify that a file is created in the correct local directory.
    -   Verify that the content of the local file matches the original.
-   **`resolveMmsPartsMeta`:**
    -   Test with an MMS containing an image, ensuring a `MessagePart` of type `IMAGE` is created with a valid local `Uri`.
    -   Test with an MMS containing text and video.
-   **`scanMmsAddrs`:**
    -   Test with an MMS sent to multiple recipients (To, Cc) and verify all addresses are returned with the correct types.
-   **`parseSmil`:**
    -   Test with a standard SMIL file and verify the correct `partOrder` is extracted.
    -   Test with a SMIL file that has no presentation data.
    -   Test with malformed SMIL XML to ensure it doesn't crash.

### 4.2. Integration Tests

-   Query a real MMS message from the content provider and run it through the entire `scanMms` flow.
-   Verify that the resulting `SmsItem` object contains the correct addresses, text parts, and `MessagePart` objects for all attachments.
-   Verify that all attachments have been successfully copied to local storage.
-   Verify that the `smilLayout` object is correctly populated if a SMIL part exists.
