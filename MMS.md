# MMS Implementation Plan

## 1. Requirements

The goal of this implementation is to add comprehensive support for Multimedia Messaging Service (MMS) to the Relay-Android application. This includes the ability to correctly parse, store, and display MMS messages, including their multimedia content.

### 1.1. Functional Requirements

- The application must be able to parse and ingest all parts of an MMS message, including text, images, videos, and audio.
- The application must be able to store the content of MMS attachments locally.
- The application must be able to display MMS messages in a way that reflects their original structure and content.
- The application must handle various character encodings and content transfer encodings.
- The application must be able to parse and interpret SMIL (Synchronized Multimedia Integration Language) files to determine the layout and timing of MMS content.

### 1.2. Non-Functional Requirements

- The MMS parsing and ingestion process should be efficient and not block the UI thread.
- The local storage of MMS attachments should be managed to avoid excessive disk space usage.
- The implementation should be robust and handle malformed or incomplete MMS messages gracefully.

## 2. Specifications

### 2.1. Data Model Changes

The existing `SmsItem` data model in `Models.kt` needs to be extended to support MMS attachments.

#### 2.1.1. `MessagePart` Data Class

A new data class, `MessagePart`, will be created to represent a single part of an MMS message.

```kotlin
// In Models.kt
data class MessagePart(
    val partId: Long,
    val messageId: Long,
    val contentType: String,
    val contentUri: String, // Local URI for the stored attachment
    val filename: String?,
    val text: String?,
    val isAttachment: Boolean
)
```

#### 2.1.2. `SmsItem` Modification

The `SmsItem` data class will be modified to include a list of `MessagePart` objects.

```kotlin
// In Models.kt
data class SmsItem(
    // ... existing fields
    val parts: List<MessagePart> = emptyList()
)
```

### 2.2. Message Ingestion Logic (`MessageScanner.kt`)

The `MessageScanner.scanMms` and related functions will be updated to handle the new requirements.

#### 2.2.1. `scanMms` Function

The `scanMms` function will be modified to:
1.  Iterate through all parts of an MMS message.
2.  For each part, determine if it is a text part or an attachment.
3.  For text parts, extract the text content.
4.  For attachments (images, videos, audio), copy the content from the `Telephony.Mms.Part` content provider to the application's local storage.
5.  Create a `MessagePart` object for each part and add it to the `SmsItem`.

#### 2.2.2. Local Storage

A new directory will be created in the application's local storage to store MMS attachments. A possible path would be `context.filesDir/mms_attachments`.

A helper function will be created to copy the content of an MMS part to a local file. This function will take the `Uri` of the MMS part as input and return the `Uri` of the newly created local file.

#### 2.2.3. SMIL Parsing

A new function, `parseSmil`, will be created to parse `application/smil` content. This function will:
1.  Take the SMIL XML string as input.
2.  Use an XML parser (e.g., `XmlPullParser`) to extract the layout and timing information.
3.  Return a data structure that represents the SMIL presentation, which can be used by the UI to display the MMS content in the correct order and layout.

### 2.3. UI/UX Considerations (Future Work)

While the UI implementation is out of scope for this plan, the data model changes should support the following UI features:
- Displaying MMS messages with their text and attachments inline.
- Allowing users to view and save MMS attachments.
- Rendering MMS messages with a slideshow-like presentation based on the parsed SMIL data.

## 3. Implementation Plan

### 3.1. Phase 1: Data Model and Storage

1.  **Modify `Models.kt`:**
    *   Create the `MessagePart` data class.
    *   Add the `parts` field to the `SmsItem` data class.
2.  **Create Local Storage Helper:**
    *   Implement a function to create the `mms_attachments` directory.
    *   Implement a function to copy an MMS part's content to a local file and return its `Uri`.

### 3.2. Phase 2: MMS Scanner Enhancement

1.  **Update `MessageScanner.kt`:**
    *   Modify `scanMms` to iterate through all MMS parts.
    *   In the loop, check the `Content-Type` of each part.
    *   If it's a text part, create a `MessagePart` with the text content.
    *   If it's an attachment, use the local storage helper to save the content and create a `MessagePart` with the local `Uri`.
    *   Handle `Content-Transfer-Encoding` (e.g., `base64`) when reading the part's data.
2.  **Implement SMIL Parser:**
    *   Create the `parseSmil` function using `XmlPullParser`.
    *   The parser should extract the `src` attribute from `<img>`, `<video>`, `<audio>`, and `<text>` tags to get the content references.
    *   The parser should also extract layout information from `<region>` tags and timing information from `<par>` tags.

## 4. Test Cases

### 4.1. Unit Tests

- **`MessageScanner.scanMms`:**
    - Test with a simple MMS with one text part and one image part.
    - Test with an MMS with multiple text and image parts.
    - Test with an MMS that has a SMIL file.
    - Test with an MMS that has `base64` encoded content.
    - Test with a malformed MMS message.
- **SMIL Parser:**
    - Test with a simple SMIL file with one slide.
    - Test with a SMIL file with multiple slides and different layouts.
    - Test with a malformed SMIL file.

### 4.2. Integration Tests

- Test the end-to-end flow of receiving an MMS message and verifying that it is correctly parsed and stored in the local database.
- Test that the MMS attachments are correctly saved to the local storage.

### 4.3. Manual Tests

- Send various types of MMS messages to a test device and verify that they are correctly displayed in the application.
- Test with MMS messages from different carriers.
- Test with large MMS attachments to ensure they are handled correctly.
- Verify that the application gracefully handles cases where MMS download fails.
