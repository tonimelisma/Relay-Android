

# **Parsing Mobile Messages on Android: A Technical Deep Dive into SMS, MMS, and RCS Protocols, Content Providers, and Libraries**

## **Section 1: Introduction and Protocol Evolution**

### **1.1. Executive Summary**

This report provides an exhaustive technical analysis of the protocols, frameworks, and libraries involved in parsing and processing Short Message Service (SMS), Multimedia Messaging Service (MMS), and Rich Communication Services (RCS) on the Android operating system. It is intended to serve as a definitive guide for software architects and senior developers engaged in building, maintaining, or reverse-engineering applications with deep messaging integration. The analysis proceeds from a foundational examination of each protocol's low-level data structure—the SMS Protocol Data Unit (PDU), the multipart MIME format of MMS, and the GSMA Universal Profile for RCS—to a practical investigation of the native Android frameworks used to interact with them. A detailed exploration of the android.provider.Telephony content provider, its underlying database schema, and the BroadcastReceiver intent system for intercepting live messages is provided. The report critically evaluates the landscape of open-source libraries available for these tasks and concludes with a synthesis of common development challenges, best practices, and a forward-looking perspective on the future of mobile messaging on the Android platform.

### **1.2. The Lineage of Mobile Messaging**

The evolution of mobile messaging is a story of technological progression, reflecting the broader development of cellular networks from simple voice and text conduits to high-bandwidth data platforms. This lineage begins with the foundational protocol, SMS, and advances through the multimedia capabilities of MMS to the modern, IP-based feature set of RCS.  
**SMS (Short Message Service)** stands as the bedrock of mobile messaging. Conceived in the 1980s and standardized as part of the Global System for Mobile Communications (GSM) specifications, SMS was designed to leverage the network's existing signaling channels.1 This design choice, while limiting its payload capacity, endowed it with unparalleled reliability and ubiquity. It operates on the circuit-switched infrastructure, meaning a message can often be delivered even when data networks are unavailable, making it a universal and dependable communication layer.2  
**MMS (Multimedia Messaging Service)** represents the first significant evolution, engineered to overcome the 160-character, text-only limitation of SMS. MMS introduced the ability to send multimedia content, including images, audio clips, and short videos. Architecturally, it functions as a "store-and-forward" service, using a combination of SMS, the Wireless Application Protocol (WAP), and mobile data networks. An incoming MMS is first signaled to the device via a WAP Push notification sent over SMS; the device then establishes a data connection to a Multimedia Messaging Service Center (MMSC) to download the full content.3 The content itself is structured as a multipart message using the Multipurpose Internet Mail Extensions (MIME) standard, a format borrowed from email to encapsulate various media types into a single payload.4  
**RCS (Rich Communication Services)** is the modern, IP-based successor intended to unify and replace the aging SMS and MMS protocols. Spearheaded by the GSM Association (GSMA), RCS was conceived as a standardized, carrier-integrated platform to provide features competitive with Over-The-Top (OTT) messaging applications like WhatsApp and iMessage.5 Operating entirely over data networks (LTE, 5G, or Wi-Fi), RCS delivers a rich feature set including typing indicators, read receipts, high-resolution media sharing, group chat, and a framework for advanced business messaging, often referred to as Messaging as a Platform (MaaP).5  
The progression from SMS to RCS is not merely an addition of features but a fundamental architectural shift from the constrained, circuit-switched signaling of 2G networks to the flexible, high-bandwidth capabilities of modern all-IP mobile networks. This evolution has consistently navigated a complex tension between the desire for universal standards and the persistent reality of fragmentation. SMS achieved near-perfect standardization due to its simple, rigid definition within the global GSM standard.1 MMS, however, introduced significant fragmentation; varying carrier limits on file sizes, supported media formats, and the need for correct Access Point Name (APN) settings created an inconsistent and often unreliable user experience.8 RCS was designed to solve this very problem through the "Universal Profile," a single, industry-agreed specification.6 Yet, early carrier-led implementations before the profile's widespread adoption created their own interoperability challenges. This pattern was ultimately broken by Google's Jibe platform, which provided a centralized backend that enforced a de facto standard, effectively solving the fragmentation problem but also concentrating control of the ecosystem.11 This history demonstrates that achieving true universality in a decentralized global carrier network is a profound challenge, often requiring a dominant player to enforce consistency.

### **1.3. High-Level Protocol Comparison**

To provide context for the detailed analysis in subsequent sections, the following table offers a high-level comparison of the three core messaging protocols.

| Feature | SMS (Short Message Service) | MMS (Multimedia Messaging Service) | RCS (Rich Communication Services) |
| :---- | :---- | :---- | :---- |
| **Transport Layer** | GSM Signaling Channels (Circuit-Switched) | WAP over Mobile Data (Packet-Switched) | IP (Packet-Switched) over Mobile Data/Wi-Fi |
| **Primary Content** | Plain Text (7-bit, 8-bit, 16-bit UCS-2) | Multimedia (Images, Audio, Video), Text | Rich Text, High-Res Media, Files, Interactive Cards |
| **Character Limit** | 160 (7-bit), 140 (8-bit), 70 (16-bit) per part | Varies by carrier, but generally larger than SMS | Effectively unlimited (e.g., 250,000 characters) 13 |
| **Interactivity** | None | Limited (via SMIL presentation) | High (Typing indicators, read receipts, suggested actions) |
| **Group Chat** | Not natively supported (emulated by clients) | Supported, often with limitations | Native, feature-rich support for up to 100 users 13 |
| **Standardization** | Highly standardized (GSM 03.40, 03.38) | Standardized but fragmented by carrier implementation | GSMA Universal Profile |
| **Dependencies** | Cellular Signal | Cellular Signal & Mobile Data Connection (APN) | Internet Connection (Mobile Data or Wi-Fi) |

---

## **Section 2: The SMS Protocol: Deconstructing the PDU**

### **2.1. PDU Mode vs. Text Mode**

When interacting with SMS messages at a low level, such as via an AT command set with a GSM modem or when parsing raw broadcast data on Android, two modes are available: Text Mode and Protocol Data Unit (PDU) Mode.2 Text Mode is a high-level abstraction that presents the message content as human-readable text, but it is limited by the character sets and encodings supported by the device or modem.2  
PDU Mode, in contrast, provides direct, unmediated access to the raw binary structure of the SMS message as it is transmitted over the network. It is represented as a string of hexadecimal characters encoding a sequence of octets (bytes). While more complex to parse, PDU mode is the canonical format and is essential for any application that requires full control and access to all message metadata and content types, including binary data, Unicode characters, and special features like Flash SMS.2 All subsequent analysis in this section focuses exclusively on the PDU format, as it is the ground truth for SMS data.

### **2.2. Anatomy of an SMS-DELIVER TPDU**

For an incoming message received by a mobile station (mobile-terminated), the relevant structure is the SMS-DELIVER Transfer Protocol Data Unit (TPDU). This PDU is encapsulated within lower-level network protocols but contains all the application-level information about the message. Its structure is a masterclass in data compression and bit-level efficiency, a direct consequence of the severe memory and bandwidth limitations of early GSM hardware. The use of semi-octets, packed 7-bit characters, and multi-flag octets reflects a design philosophy born from scarcity, which explains why manual parsing requires meticulous bitwise operations.14  
The SMS-DELIVER TPDU consists of the following key fields in sequence 15:

#### **SCA (Service Center Address)**

This optional field specifies the address (phone number) of the Short Message Service Center (SMSC) that relayed the message. It begins with a length octet indicating the number of subsequent octets, including the Type-of-Address octet. The SMSC number itself is encoded in Binary-Coded Decimal (BCD) format, where each octet contains two decimal digits (semi-octets) with nibbles swapped.14

#### **PDU Type**

This is a single, crucial octet that contains a collection of bit-flags defining the message's characteristics and instructing the receiving entity on how to handle it. Key subfields include 15:

* **TP-MTI (Message Type Indicator):** A 2-bit field indicating the PDU type. For an incoming SMS-DELIVER, this is always 00\.  
* **TP-MMS (More Messages to Send):** A 1-bit flag indicating if more messages are waiting for the device at the SMSC.  
* **TP-SRI (Status Report Indication):** A 1-bit flag indicating if the sending entity requested a status report.  
* **TP-UDHI (User Data Header Indicator):** A critical 1-bit flag. If set to 1, it indicates that the User Data (TP-UD) field begins with a User Data Header (UDH), which is used for advanced features like concatenated (long) messages.15  
* **TP-RP (Reply Path):** A 1-bit flag indicating if a reply path is set, allowing the recipient to reply via the same SMSC.

#### **TP-OA (Originator Address)**

This field contains the sender's phone number and can be 2 to 12 octets long. It is structured similarly to the SCA, with a length field (representing the number of BCD digits, not octets), a Type-of-Address octet (e.g., $91 for international format, $81 for national), and the number itself encoded in semi-octets. If the phone number has an odd number of digits, a filler F is appended to the last semi-octet to complete the octet.14

#### **TP-PID (Protocol Identifier)**

This single octet indicates the presence of a higher-layer protocol or specific interworking requirements, such as routing the message to a fax machine, voice mail, or for SIM/UICC-specific data downloads.15 For standard text messages, it is typically  
$00.

#### **TP-DCS (Data Coding Scheme)**

This octet is vital for correctly interpreting the message content in the TP-UD field. It specifies the character alphabet and message class. Key values include 2:

* **GSM 7-bit Default Alphabet:** The standard encoding for most text messages, allowing 160 characters. The DCS value also indicates if a message class is present (e.g., Class 0 for a "Flash SMS" that is displayed immediately and not stored).  
* **8-bit Data:** Used for binary messages, such as ringtones or operator logos, with a maximum length of 140 characters (bytes).  
* **16-bit UCS-2:** Used for Unicode characters to support international languages. This encoding limits the message length to 70 characters.

#### **TP-SCTS (Service Center Time Stamp)**

This 7-octet field represents the time and date when the SMSC received the message. Like the phone numbers, it is encoded in semi-octets, with each pair of digits representing year, month, day, hour, minute, second, and timezone offset, respectively.14

#### **TP-UDL (User Data Length)**

This single octet specifies the length of the TP-UD field. Its interpretation depends on the TP-DCS. If the encoding is GSM 7-bit, the TP-UDL value is the number of septets (characters) in the message (up to 160). For 8-bit or 16-bit UCS-2 encoding, it represents the number of octets in the user data.15

#### **TP-UD (User Data)**

This is the actual payload of the message. For GSM 7-bit encoding, the characters are packed together, with 8 characters fitting into 7 octets. This requires a bit-shifting algorithm to unpack and decode correctly. For 8-bit and 16-bit messages, the data is aligned on octet boundaries and can be read directly. If the TP-UDHI flag is set, this field begins with the User Data Header.

### **2.3. Handling Concatenated SMS and the User Data Header (UDH)**

To send messages longer than the single-SMS limit, the Concatenated SMS feature is used. This is enabled via the User Data Header (UDH), a mechanism that provides an extensible way to add features on top of the basic SMS transport, foreshadowing the layered architectures of later protocols. The UDH is a prime example of early protocol designers planning for future extensibility within severe constraints.  
When a long message is sent, it is split into multiple SMS parts. The TP-UDHI bit in the PDU Type field is set to 1 for each part, signaling the presence of a UDH at the beginning of the TP-UD field.15 The UDH itself has a simple structure:

* **UDHL (User Data Header Length):** The first octet of the UDH, specifying its own length in octets.  
* **Information Elements (IEs):** A series of Type-Length-Value (TLV) formatted elements. For concatenated messages, a specific Information Element Identifier (IEI) is used (e.g., $00 for 8-bit reference number or $08 for 16-bit reference). This IE is followed by its length and the data, which includes a reference number (the same for all parts of the same message), the total number of parts, and the sequence number of the current part.15

A receiving application must parse the UDH from each incoming part, collect all parts with the same reference number, order them by their sequence number, and then concatenate their user data portions to reconstruct the original long message.  
---

## **Section 3: The MMS Protocol: A Multipart/MIME Approach**

### **3.1. MMS Message Architecture**

The Multimedia Messaging Service (MMS) was designed to transcend the limitations of SMS by enabling the exchange of rich media. Rather than inventing a new data format from scratch, its architects made a pragmatic decision to integrate existing, mature internet standards, primarily the Multipurpose Internet Mail Extensions (MIME) format used ubiquitously in email.3 This makes an MMS message, at its core, an encapsulated MIME entity. This hybrid approach, combining standards from email (MIME), web presentations (SMIL), and mobile notifications (WAP Push), is both a source of its power and its complexity. A single MMS transaction involves multiple protocols and layers, creating several potential points of failure, from WAP Push delivery issues to data connection problems or MIME parsing errors. This contrasts sharply with the monolithic design of SMS and helps explain the reputation MMS has for being less reliable.

### **3.2. The Multipart Structure**

An MMS message is structured as a multipart MIME message, where different pieces of content (text, images, etc.) are bundled together, each with its own descriptive headers. The overall structure is defined by the primary Content-Type header of the message body, which is typically one of two types 4:

* **multipart/related:** This type is used when the message includes a "root" document that defines the relationship between the other parts. In MMS, this root is almost always a Synchronized Multimedia Integration Language (SMIL) file. The Content-Type header will include a type parameter that points to the MIME type of the root document (e.g., type="application/smil") and a start parameter pointing to its Content-ID. The SMIL file then orchestrates the presentation of the other media parts.4  
* **multipart/mixed:** This type is used for a simpler collection of disparate parts that have no specific relationship to each other beyond being in the same message. If no SMIL file is present, multipart/mixed is used, and the parts are typically intended to be displayed in the order they appear in the message body.4

In both cases, a boundary parameter in the Content-Type header defines a unique string that is used to separate each part within the message body. Each boundary line starts with two hyphens (--), and the final boundary line also ends with two hyphens.3

### **3.3. The Role of SMIL (Synchronized Multimedia Integration Language)**

The inclusion of SMIL marks a significant philosophical shift from the simple content delivery of SMS to the curated user experience of MMS. SMIL, an XML-based markup language standardized by the W3C, allows the sender to define the layout, timing, and synchronization of the various media elements in the message.3 This moves MMS beyond a simple file transfer service into a lightweight multimedia authoring platform, a direct precursor to the "rich cards" and interactive elements found in modern RCS and OTT applications.7  
In practice, MMS clients implement a minimal subset of the full SMIL specification, often referred to as "MMS SMIL".21 A typical MMS SMIL file defines a "slide show" presentation. It contains tags to:

* Define the layout, such as regions on the screen for an image and for text.  
* Specify the source of the media for each region, referencing other MIME parts via their Content-ID (e.g., \<img src="cid:image1.jpg" region="Image"/\>).17  
* Set the duration for each slide and synchronize audio or video playback with the display of text and images.3

### **3.4. Parsing Individual MIME Parts**

The core of parsing an MMS message lies in iterating through each MIME part delineated by the boundary string and interpreting its headers and body. Each part is a self-contained block of data with its own set of headers 17:

* **Content-Type:** Specifies the media type of the part's content, such as text/plain, image/jpeg, image/png, video/mp4, or text/vcard. It may also include a charset parameter (e.g., charset=utf-8) for text parts.17  
* **Content-ID:** A unique identifier for the part, enclosed in angle brackets (e.g., \<image1.jpg\>). This ID is used by the SMIL file to reference and place the content.17  
* **Content-Location:** Often provides the original filename of the content.  
* **Content-Transfer-Encoding:** Specifies the encoding used for the part's body. While binary is the default, base64 is commonly used to ensure that binary data can be safely transmitted through text-based systems. If the encoding is base64, the body must be decoded before it can be used.17

After parsing the headers, the application reads the body of the part until the next boundary string is encountered. If the part is text-based, the body is the string content. If it is binary, the body is the raw byte data of the image, video, or other file. The most widely supported media formats are JPEG, GIF, and PNG for images, and MP4 containers with H.264 video and AAC audio for video files.17  
---

## **Section 4: The RCS Protocol: The Universal Profile and the Future of Messaging**

### **4.1. From Fragmentation to Unification: The Universal Profile**

Rich Communication Services (RCS) represents the telecommunications industry's effort to create a modern, IP-based messaging standard to succeed SMS and MMS. However, initial deployments by individual mobile network operators (MNOs) were plagued by fragmentation, with different carriers implementing incompatible versions of the protocol, thereby failing to deliver the promise of seamless, cross-carrier communication.11 To solve this critical issue, the GSMA spearheaded the creation of the  
**Universal Profile (UP)**, a single, industry-agreed specification that defines a common set of features and technical enablers.5  
The Universal Profile serves as the definitive standard that operators, Original Equipment Manufacturers (OEMs), and operating system providers must adhere to, ensuring that an RCS message sent from a user on one network can be successfully received and rendered by a user on any other compliant network. This has been crucial in simplifying product development and accelerating the global deployment of RCS.6

### **4.2. Core Features of the Universal Profile**

The Universal Profile defines the rich, interactive features that are designed to bring native carrier messaging to parity with popular OTT apps. Key features include 5:

* **Capability Discovery:** Before an RCS session is initiated, a client can perform a capability exchange to determine which RCS features the recipient's device supports. This allows the client to gracefully fall back to SMS/MMS if the recipient is not RCS-enabled, ensuring message delivery.5  
* **Rich Chat:** This encompasses a suite of features that enhance one-to-one and group conversations, such as real-time **typing indicators**, **read receipts** (confirming a message has been read), and robust **group chat** functionality that goes far beyond the limitations of MMS-based group messaging.5  
* **Enhanced Media Sharing:** A significant upgrade from MMS, RCS supports the transfer of **high-resolution photos and videos** and **large files** (up to 100MB or more), avoiding the heavy compression and restrictive size limits imposed by MNOs on MMS.5  
* **Business Messaging (MaaP):** The Universal Profile lays the groundwork for Messaging as a Platform (MaaP), enabling businesses to engage with customers through verified profiles, interactive **Rich Cards** (which can display images, text, and buttons), **suggested replies**, and **suggested actions** (like opening a URL, dialing a number, or viewing a location). This facilitates conversational commerce and chatbot integration directly within the native messaging app.7

### **4.3. The Evolving Standard: Recent UP Versions**

The Universal Profile is not a static document; it is continuously evolving to incorporate new features, largely in response to the competitive landscape of OTT messaging apps. The development of the UP is not happening in a vacuum but is a clear attempt to achieve feature parity with services like iMessage and WhatsApp. Recent updates introducing reactions, editing, and end-to-end encryption are direct responses to consumer expectations shaped by this competitive market.

* **UP 2.x/3.0:** These versions introduced significant enhancements, particularly for user engagement and business messaging. Key additions include support for **message reactions** (allowing users to react to a message with an emoji), the ability for a user to **edit a message** they have already sent, and improved chatbot functionalities.7  
* **UP 3.1:** This update focused on improving the underlying reliability and quality of the service. It introduced smarter client-server connection management to enhance performance on poor networks and support for the **xHE-AAC audio codec**, providing a noticeable improvement in the quality of voice notes and other audio messages without increasing data consumption.24  
* **Security Enhancements:** A pivotal development has been the integration of interoperable **End-to-End Encryption (E2EE)** using the Messaging Layer Security (MLS) protocol. This addresses a major consumer concern regarding privacy and security, positioning RCS as a more secure alternative to the unencrypted SMS/MMS protocols and a direct competitor to encrypted OTT services.7

### **4.4. The Central Role of Google Jibe**

While RCS is a GSMA standard, its practical implementation and rollout in the Android ecosystem have been overwhelmingly driven by Google. This has led to a power shift, where Google, rather than individual carriers, has become the central force and de facto gatekeeper of the RCS network.

* **The Jibe Platform:** Recognizing that the cost and complexity of deploying IMS-based RCS infrastructure was a major barrier for carriers, Google acquired Jibe Mobile and developed the **Jibe Platform**. This is a Google-hosted cloud service that provides a turnkey solution for MNOs to launch and manage RCS services, effectively outsourcing the infrastructure to Google.25  
* **The Jibe Hub:** To solve the critical problem of interoperability, the Jibe Platform includes the **Jibe Hub**. This acts as a central interconnection point, allowing RCS networks hosted by Jibe to seamlessly communicate with other RCS networks, whether they are also on Jibe or are independently operated by a carrier. This hub is what makes the "Universal" aspect of the profile a reality for the majority of the world's RCS users.26  
* **Google Messages:** Google's own "Messages" application serves as the reference client for the Universal Profile on Android. By making it the default messaging app on most Android devices, Google controls the user-facing implementation of RCS, ensuring a consistent experience and driving the adoption of new features like E2EE.11

This three-pronged approach—providing the backend infrastructure (Jibe Platform), ensuring interoperability (Jibe Hub), and controlling the client application (Google Messages)—has enabled Google to overcome the carrier fragmentation that initially stalled RCS adoption. However, it has also centralized a once-decentralized standard, making the health and future of the P2P RCS ecosystem on Android heavily dependent on Google's strategic decisions.  
---

## **Section 5: The Android Telephony Content Provider: The Unified Message Store**

### **5.1. Architectural Overview**

The primary mechanism for an Android application to access stored SMS and MMS messages is through the android.provider.Telephony class. This class serves as the public contract for the Telephony content provider, which is an abstraction layer over an underlying SQLite database.28 This database, typically named  
mmssms.db, is located in the application data directory for the telephony provider package (/data/data/com.android.providers.telephony/databases/) and is not directly accessible to other applications.30 All interactions must occur through the  
ContentResolver using the URIs defined in the Telephony contract class.  
To simplify development for applications that present conversations, Android provides a unified Telephony.MmsSms provider. This virtual provider presents a merged view of messages from both the SMS and MMS tables, ordered by date, allowing an app to query a single URI to build a complete conversation thread.31 A special column,  
TYPE\_DISCRIMINATOR\_COLUMN, can be requested in the projection to distinguish whether a given row represents an "sms" or an "mms" message.31

### **5.2. Querying SMS Messages**

Accessing stored SMS messages is a straightforward process involving a standard ContentResolver query.

* **Content URIs:** The Telephony.Sms class and its nested classes define the URIs for different message boxes. The most common are:  
  * Telephony.Sms.CONTENT\_URI (content://sms): Accesses all SMS messages.  
  * Telephony.Sms.Inbox.CONTENT\_URI (content://sms/inbox): Accesses received messages.29  
  * Telephony.Sms.Sent.CONTENT\_URI (content://sms/sent): Accesses messages sent by the user.  
* **Key Columns:** The columns available in the cursor are defined in the Telephony.TextBasedSmsColumns interface. The most critical columns for parsing are:  
  * \_id: The unique ID for the message.  
  * thread\_id: The ID of the conversation thread this message belongs to.  
  * address: The phone number of the other party.  
  * body: The text content of the message.  
  * date: The timestamp of the message (in milliseconds since epoch).  
  * type: An integer indicating the message box (e.g., MESSAGE\_TYPE\_INBOX, MESSAGE\_TYPE\_SENT).29  
* **Code Example (Kotlin):** The following demonstrates a basic query to read all messages from the inbox.

Kotlin

import android.content.ContentResolver  
import android.provider.Telephony  
import android.database.Cursor

fun readSmsInbox(contentResolver: ContentResolver) {  
    val projection \= arrayOf(  
        Telephony.Sms.ADDRESS,  
        Telephony.Sms.BODY,  
        Telephony.Sms.DATE  
    )  
    val cursor: Cursor? \= contentResolver.query(  
        Telephony.Sms.Inbox.CONTENT\_URI,  
        projection,  
        null,  
        null,  
        Telephony.Sms.DEFAULT\_SORT\_ORDER  
    )

    cursor?.use { c \-\>  
        val addressIndex \= c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)  
        val bodyIndex \= c.getColumnIndexOrThrow(Telephony.Sms.BODY)  
        val dateIndex \= c.getColumnIndexOrThrow(Telephony.Sms.DATE)

        while (c.moveToNext()) {  
            val address \= c.getString(addressIndex)  
            val body \= c.getString(bodyIndex)  
            val date \= c.getLong(dateIndex)  
            // Process the SMS data  
            println("From: $address, Body: $body, Date: $date")  
        }  
    }  
}

### **5.3. Querying MMS Messages: A Two-Step Process**

Retrieving the full content of an MMS message is more complex than for SMS because its multipart structure is represented in the database by a relational schema. The content provider's design is a direct reflection of the MMS protocol's structure: a parent mms table stores the container-level metadata, while a child part table stores the individual MIME parts. The MSG\_ID column in the part table serves as a foreign key, creating a one-to-many relationship that perfectly mirrors the container-to-parts structure of a MIME message.32 Therefore, parsing a stored MMS requires a process analogous to a SQL JOIN, performed via two separate queries.

* **Step 1: Querying MMS Metadata:** The first step is to query the main MMS table using Telephony.Mms.CONTENT\_URI (content://mms) to get a list of messages and their top-level metadata. The most important column to retrieve from this query is \_id, which is the unique identifier for the MMS message. This ID will be used as the foreign key to retrieve the message's content parts.36  
* **Step 2: Querying MMS Parts:** For each MMS message retrieved in Step 1, a second query must be performed on the parts table using Telephony.Mms.Part.CONTENT\_URI (content://mms/part). The selection clause for this query must filter for parts belonging to the message from Step 1, using the \_id obtained previously. The selection would be Telephony.Mms.Part.MSG\_ID \+ " \=?", with the message ID passed as a selection argument.35

### **5.4. Parsing MMS Parts**

The cursor returned from the second query on the part table contains the individual components of the MMS message. Iterating through this cursor and examining the CONTENT\_TYPE (ct) column is the key to correctly parsing the content.

#### **Table: Key Columns of the Telephony.Mms.Part Provider**

This table provides a crucial reference for developers, mapping the often-cryptic column names of the part table to their function and data type. This consolidation of information is essential for efficiently and accurately parsing MMS content.

| Column Name (Telephony.Mms.Part Constant) | Data Type | Description |
| :---- | :---- | :---- |
| \_ID | INTEGER | The unique ID for this specific part. |
| MSG\_ID (mid) | INTEGER | Foreign key linking this part to the \_id in the main mms table. **Crucial for selection.** 35 |
| CONTENT\_TYPE (ct) | TEXT | The MIME type of the part (e.g., text/plain, image/jpeg, application/smil). **Used to determine how to handle the content.** 35 |
| TEXT (text) | TEXT | The content of the part if it is text-based (e.g., text/plain or application/smil). 35 |
| \_DATA (\_data) | TEXT | The absolute file path to the cached data for this part if it is a binary attachment (e.g., an image or video). If this is null, the data must be read via an InputStream from the part's URI. 35 |
| CONTENT\_ID (cid) | TEXT | The Content-ID of the part, used to reference it from a SMIL presentation file. 35 |
| FILENAME (fn) | TEXT | The original filename of the part, if available. 35 |
| CONTENT\_LOCATION (cl) | TEXT | The Content-Location of the part, often used interchangeably with FILENAME. 35 |

* **Code Example (Kotlin):** The following demonstrates the full two-step process for reading MMS messages and their parts.

Kotlin

import android.content.ContentResolver  
import android.net.Uri  
import android.provider.Telephony  
import java.io.InputStream  
import java.nio.charset.StandardCharsets

fun readMmsMessages(contentResolver: ContentResolver) {  
    val mmsProjection \= arrayOf(Telephony.Mms.\_ID, Telephony.Mms.THREAD\_ID)  
    val mmsCursor \= contentResolver.query(  
        Telephony.Mms.Inbox.CONTENT\_URI,  
        mmsProjection,  
        null,  
        null,  
        Telephony.Mms.DEFAULT\_SORT\_ORDER  
    )

    mmsCursor?.use { mmsC \-\>  
        val idIndex \= mmsC.getColumnIndexOrThrow(Telephony.Mms.\_ID)  
        while (mmsC.moveToNext()) {  
            val mmsId \= mmsC.getString(idIndex)  
            println("--- Found MMS with ID: $mmsId \---")

            val partProjection \= arrayOf(  
                Telephony.Mms.Part.CONTENT\_TYPE,  
                Telephony.Mms.Part.\_DATA,  
                Telephony.Mms.Part.TEXT  
            )  
            val selection \= "${Telephony.Mms.Part.MSG\_ID} \=?"  
            val selectionArgs \= arrayOf(mmsId)  
            val partCursor \= contentResolver.query(  
                Telephony.Mms.Part.CONTENT\_URI,  
                partProjection,  
                selection,  
                selectionArgs,  
                null  
            )

            partCursor?.use { partC \-\>  
                val ctIndex \= partC.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT\_TYPE)  
                val dataIndex \= partC.getColumnIndexOrThrow(Telephony.Mms.Part.\_DATA)  
                val textIndex \= partC.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)

                while (partC.moveToNext()) {  
                    val contentType \= partC.getString(ctIndex)  
                    when {  
                        contentType.startsWith("image/") \-\> {  
                            val partId \= partC.getString(partC.getColumnIndexOrThrow(Telephony.Mms.Part.\_ID))  
                            val imageUri \= Uri.withAppendedPath(Telephony.Mms.Part.CONTENT\_URI, partId)  
                            println("Found Image Part: $imageUri")  
                            // Use imageUri to load the image, e.g., with an image loading library  
                        }  
                        contentType.startsWith("text/") \-\> {  
                            val text \= partC.getString(textIndex)  
                            println("Found Text Part: $text")  
                        }  
                        contentType \== "application/smil" \-\> {  
                            val smilText \= partC.getString(textIndex)  
                            println("Found SMIL Part: $smilText")  
                        }  
                        // Add cases for video, audio, etc.  
                    }  
                }  
            }  
        }  
    }  
}

---

## **Section 6: Intercepting Messages with Broadcast Receivers**

### **6.1. The Role of BroadcastReceiver in Real-Time Processing**

To process messages in real-time as they arrive on the device, an application must implement a BroadcastReceiver. This Android component is designed to listen for system-wide broadcast Intents. When a specific event occurs, such as the arrival of a new SMS, the Android system dispatches an Intent that can be intercepted by any application that has registered a receiver for that specific action.39 This mechanism allows an app to react to a message immediately, before it is even written to the Telephony content provider, enabling use cases like two-factor authentication (2FA) code extraction, message blocking, or custom notifications.

### **6.2. Handling Incoming SMS**

To intercept an incoming SMS, a BroadcastReceiver must be registered with an intent filter for the appropriate action.

* **Action:** android.provider.Telephony.Sms.Intents.SMS\_RECEIVED\_ACTION is the broadcast action that non-default SMS apps should listen for.40  
* **Permission:** The application manifest (AndroidManifest.xml) must declare the android.permission.RECEIVE\_SMS permission. For apps targeting Android 6.0 (API level 23\) and higher, this permission must also be requested from the user at runtime.40  
* **Extras:** The broadcast Intent contains an extra with the key pdus. This extra is an Object where each element is a byte array representing the raw PDU of an SMS message part.44 For long messages, this array will contain multiple PDUs.  
* **Parsing:** To parse the content, the application should retrieve the pdus extra from the intent and use the static method SmsMessage.createFromPdu((byte) pdu) to convert each raw PDU byte array into a structured SmsMessage object. From this object, the originating address, message body, and other metadata can be easily accessed. For concatenated messages, the application must iterate through all SmsMessage objects, group them by originating address, and assemble the message bodies in the correct order to reconstruct the full message.42

### **6.3. Handling Incoming MMS**

Intercepting an incoming MMS notification follows a similar pattern but listens for a different action and requires a specific MIME type in its intent filter.

* **Action:** android.provider.Telephony.Sms.Intents.WAP\_PUSH\_RECEIVED\_ACTION is the broadcast action for an incoming WAP Push message, which is the mechanism used to signal a new MMS.44  
* **Permission:** The manifest must declare the android.permission.BROADCAST\_WAP\_PUSH permission.47  
* **MIME Type:** Crucially, the \<intent-filter\> in the manifest must include a \<data\> tag specifying the MIME type application/vnd.wap.mms-message. Without this, the receiver will not be triggered for MMS notifications.44  
* **Extras:** The Intent contains a byte extra with the key data. This byte array is the raw PDU of the MMS Notification Indication (m-notification-ind). This PDU does not contain the full MMS content but rather metadata, most importantly the URL on the MMSC from which the full MMS message must be downloaded over a data connection.48 Parsing this PDU requires a dedicated WAP PDU parsing library or manual implementation.

### **6.4. Modern Android: The Default SMS App and Broadcast Restrictions**

The single most significant architectural change in Android's messaging framework occurred with the release of Android 4.4 (KitKat), which introduced the concept of a user-selectable "default SMS app".47 This was a deliberate policy decision to move from a simple permission-based access model to a more secure and user-friendly role-based access model. The original free-for-all broadcast system created security risks and user confusion. The default app model centralizes control in a single, trusted application, fundamentally altering how messaging apps must be designed.

* **Privileged Intents:** The system now uses two new, protected broadcast actions for primary message delivery:  
  * android.provider.Telephony.Sms.Intents.SMS\_DELIVER\_ACTION for SMS.  
  * android.provider.Telephony.Sms.Intents.WAP\_PUSH\_DELIVER\_ACTION for MMS.  
    These intents are dispatched as ordered broadcasts and are sent exclusively to the application that the user has designated as the default SMS app. This app is granted the privilege of receiving the message first and holds the responsibility of writing it to the Telephony content provider.46  
* **The Repurposed \_RECEIVED Intents:** The older SMS\_RECEIVED\_ACTION and WAP\_PUSH\_RECEIVED\_ACTION broadcasts are still sent, but only *after* the default app has processed the message and written it to the provider. They are now intended for non-default apps that need to read or react to a new message (e.g., for 2FA code verification or backup services) but are not responsible for its primary handling.28 These actions are explicitly whitelisted as exceptions to the background broadcast restrictions introduced in later Android versions, recognizing their importance.51  
* **Background Execution and Activity Start Restrictions:**  
  * **Android 8.0 (API 26):** Imposed strict limits on background execution. Manifest-declared receivers can no longer be used for most implicit broadcasts. However, the SMS/MMS-related broadcasts are critical exceptions, allowing apps to be woken from the background to process an incoming message.39  
  * **Android 10 (API 29):** Introduced restrictions on starting activities from the background. A BroadcastReceiver that is not in the foreground can no longer directly call startActivity(). This prevents jarring user experiences. The correct modern practice is for the receiver to post a high-priority notification. This notification can be associated with a PendingIntent, and for urgent alerts, a fullScreenIntent can be used to display a full-screen UI.52

#### **Table: Messaging Broadcast Intents**

This table provides a critical quick-reference for developers, summarizing the key broadcast intents, their purpose, and the constraints associated with them in the modern Android ecosystem.

| Intent Action | Target Message | Key Extra(s) | Required Permission(s) | Target Audience & API Level Notes |
| :---- | :---- | :---- | :---- | :---- |
| android.provider.Telephony.Sms.Intents.SMS\_RECEIVED\_ACTION | SMS | pdus (Object) | android.permission.RECEIVE\_SMS | All apps. Sent after the default app. An exception to background broadcast restrictions. 40 |
| android.provider.Telephony.Sms.Intents.SMS\_DELIVER\_ACTION | SMS | pdus (Object) | android.permission.BROADCAST\_SMS | **Default SMS App Only** (API 19+). This is the primary delivery intent. 47 |
| android.provider.Telephony.Sms.Intents.WAP\_PUSH\_RECEIVED\_ACTION | MMS | data (byte) | android.permission.RECEIVE\_WAP\_PUSH | All apps. Requires \<data android:mimeType="application/vnd.wap.mms-message"/\>. 44 |
| android.provider.Telephony.Sms.Intents.WAP\_PUSH\_DELIVER\_ACTION | MMS | data (byte) | android.permission.BROADCAST\_WAP\_PUSH | **Default SMS App Only** (API 19+). Primary MMS delivery intent. Requires MIME type filter. 46 |

---

## **Section 7: Analysis of Open-Source Android Messaging Libraries**

The open-source community has produced a variety of libraries and reference applications to aid in the development of messaging-aware Android apps. These tools range from focused, single-purpose libraries for sending messages to complete, full-featured client implementations that serve as architectural blueprints. The landscape for SMS/MMS libraries reflects a mature, if somewhat static, ecosystem, while the situation for RCS is starkly different, dominated by server-side APIs rather than client-side libraries.

### **7.1. SMS/MMS Parsing and Sending Libraries**

The available libraries for SMS and MMS generally fall into two categories: older, specialized sending libraries and modern, complete messaging application codebases. This reflects a "solved problem" dynamic, where the core Android APIs have become stable enough that new, lightweight parsing libraries are rare. This leaves developers to choose between using potentially unmaintained legacy code or studying large, complex applications to extract the necessary logic.

* **klinker41/android-smsmms**:  
  * **Analysis:** This library, licensed under Apache 2.0, was created to simplify the process of *sending* SMS and MMS messages, a task that can be complex due to the need to handle MMS APN settings manually on pre-Lollipop devices.53 Its core strength is its transactional API, which abstracts away the details of constructing and dispatching messages. For devices running Android 5.0 (API 21\) and higher, it provides a simplified path that leverages the system's built-in MMS sending capabilities.53 However, the library has not been updated since 2019, which raises concerns about its compatibility with the latest Android platform restrictions and best practices. It is not designed for parsing incoming messages or managing conversation threads.53  
  * **Use Case:** Best suited for legacy projects or applications that require a simple, "fire-and-forget" mechanism for sending SMS/MMS and where the maintenance status is not a critical concern.  
* **FossifyOrg/Messages**:  
  * **Analysis:** This GPL-3.0 licensed project is a complete, modern, and actively maintained open-source SMS/MMS messaging client written in Kotlin.55 It is not a library to be included as a dependency but rather a comprehensive reference implementation. Its value for a developer lies in its source code, which serves as an excellent blueprint for modern messaging app architecture. By studying the code, one can learn best practices for querying the  
    Telephony content provider, managing conversation threads, handling permissions, implementing a clean UI with Material Design principles, and dealing with background tasks in a way that is compliant with modern Android versions.55  
  * **Use Case:** An invaluable educational resource and architectural guide for developers tasked with building a full-featured SMS/MMS client from the ground up.  
* **adorsys/sms-parser-android**:  
  * **Analysis:** This is an example of a highly specialized, niche library. Its sole purpose is to intercept incoming SMS messages and parse them to extract a specific piece of information, such as a verification code, based on configurable start and end markers.56  
  * **Use Case:** Ideal for applications that need to implement features like automatic one-time password (OTP) code detection without the overhead of a full messaging library.

### **7.2. RCS Libraries and APIs**

The search for an "Android RCS library" for client-side development is fundamentally misleading. The available tools and libraries are almost exclusively designed for server-to-client (Application-to-Person, A2P) communication, not for enabling third-party apps to act as peer-to-peer (P2P) RCS clients. This is a critical distinction that shapes the entire RCS development landscape.

* **android-rcs/rcsjta**:  
  * **Analysis:** This Apache 2.0 licensed project is an open-source implementation of a full RCS-e (Rich Communication Suite-enhanced) stack, compliant with early GSMA standards.57 It is a low-level, complex project that demonstrates the feasibility of building an RCS client from first principles, requiring deep expertise in underlying protocols like IMS (IP Multimedia Subsystem) and SIP (Session Initiation Protocol). Given its age and the current dominance of Google's Jibe network, its practical application for a modern developer is limited.57  
  * **Use Case:** Primarily of academic or research interest, or for large organizations attempting to build an independent RCS network infrastructure. It is not a viable option for a typical application developer.  
* **Google's RCS Business Messaging API**:  
  * **Analysis:** This is the primary and only officially supported method for developers to interact with the RCS network. It is a **server-side REST API**, not a client-side Android library.58 Google provides client libraries in various languages, such as  
    java-rcsbusinessmessaging, but these are merely wrappers to facilitate calling the web API from a backend server.60 These APIs allow a business to send rich messages, cards, and suggestions to users who have RCS enabled. They do not provide any mechanism for an Android app on a user's device to send an RCS message on behalf of that user.62  
  * **Use Case:** Exclusively for businesses and brands to build A2P messaging experiences, such as customer support chatbots, promotional campaigns, and transactional notifications, managed and sent from their own server infrastructure.

This ecosystem structure funnels all third-party RCS development into the A2P market, which Google can control and potentially monetize, while keeping the P2P messaging space as the exclusive domain of its own Google Messages application.  
---

## **Section 8: Synthesis, Challenges, and Recommendations**

### **8.1. Comparative Development Complexity**

The effort and complexity involved in parsing and integrating with each messaging protocol on Android vary dramatically, reflecting their underlying technical designs and the level of abstraction provided by the Android framework.

* **SMS:** For basic sending and reading of stored messages, the complexity is **low**. The SmsManager and Telephony.Sms content provider offer high-level, stable APIs. The complexity becomes **high** only if a developer needs to parse raw PDUs from a broadcast, which requires bit-level manipulation and a deep understanding of the GSM specifications.  
* **MMS:** The complexity is inherently **high**. It requires a multi-step query process against the content provider, parsing of the multipart MIME structure, interpretation of an optional SMIL file for presentation logic, and handling of various content encodings.38 Furthermore, developers must contend with significant real-world unreliability stemming from carrier-specific APN settings, inconsistent file size limits, and the requirement for an active mobile data connection, many of which are outside of the application's control.8  
* **RCS (Client-side P2P):** The complexity is **prohibitively high**, to the point of being practically impossible for the vast majority of third-party developers. The challenge is not one of technical implementation but of access. The lack of a public, client-side API for the device's native RCS connection is a hard blocker.12

### **8.2. The "RCS API Gap": The Primary Blocker for Third-Party Innovation**

The central and most critical challenge for Android developers wishing to engage with modern messaging is the **"RCS API Gap."** Despite RCS being promoted as an open, universal standard, Google has not provided a public, client-side API to allow third-party applications to send and receive RCS messages using the phone's native, carrier-provisioned RCS connection.12  
This strategic omission has profound implications:

1. **It solidifies Google Messages as the sole P2P RCS client on Android.** By being the only application with access to the underlying RCS service, it creates a de facto monopoly and a "walled garden" experience, ironically mirroring the very ecosystem lock-in for which Google has criticized Apple's iMessage.12  
2. **It stifles competition and innovation.** Without an API, other developers cannot create alternative messaging clients with unique features, user interfaces, or privacy models that leverage the RCS network. This prevents a competitive market for RCS clients from emerging on Android.  
3. **It highlights the immense technical barrier.** The RCS protocol is built upon complex telecommunications standards like IMS and SIP. The sheer size and complexity of the specification make it infeasible for an independent developer or small company to build a compatible client from scratch, making an official API the only viable path for integration.65

The current state of Android messaging is the result of a clear historical progression. The initial open broadcast system for SMS was insecure and confusing. To fix this, Google introduced the "default SMS app" model, centralizing control for security and a better user experience.47 Concurrently, MMS and early RCS were crippled by carrier fragmentation. Google solved this by providing a centralized backend with its Jibe platform.26 The logical endpoint of this long-term trend of solving fragmentation through centralization is the current RCS API Gap. Having established control over both the client and the backend, there is little strategic incentive for Google to open a client-side API that could re-introduce fragmentation and weaken the position of its own Messages app.

### **8.3. Common Development Challenges and Best Practices**

Developers building messaging applications on Android must navigate a landscape of technical hurdles and platform restrictions.

* **Permission Handling:** Applications must declare all necessary permissions (e.g., RECEIVE\_SMS, READ\_SMS, SEND\_SMS) in their manifest and, for Android 6.0 (API 23\) and higher, implement a robust runtime permission request flow to gain user consent.40  
* **Background Processing:** The onReceive() method of a BroadcastReceiver executes on the main thread and has a very short execution window. Any time-consuming task, such as network requests to download MMS content or complex parsing, must be offloaded to a background thread. The recommended modern approach is to use WorkManager or JobScheduler to schedule a durable background job from the receiver, ensuring the work completes even if the app process is killed.39  
* **MMS Configuration Hell:** Developers must anticipate and handle common MMS failure points. These include incorrect or missing APN settings on the device, the mobile data connection being disabled (MMS requires it, even on Wi-Fi), and carrier-imposed file size limits that can cause messages to be rejected or heavily compressed.8 Applications should provide clear user feedback when such issues occur.  
* **RCS Unreliability and Fallback:** Even for Google Messages, RCS can be unreliable. Users frequently report issues with activation failing to complete, the connection status getting stuck, or messages intermittently falling back to SMS/MMS even with RCS-enabled contacts.69 Any system interacting with RCS must have a graceful and transparent fallback mechanism to SMS/MMS to ensure message delivery.71

### **8.4. Future Outlook**

The mobile messaging landscape remains dynamic, with two key factors poised to shape its future on Android.

* **Apple's Adoption of RCS:** Apple has committed to adopting the RCS Universal Profile. This move will largely eliminate the "green bubble/blue bubble" divide by enabling rich messaging features between Android and iOS devices. This cross-platform interoperability will significantly increase the value and user base of RCS, potentially creating renewed pressure on Google to foster a more open ecosystem on its own platform to spur innovation.  
* **The Enduring API Question:** The most significant unresolved issue is whether Google will ever release a public, client-side P2P RCS API for third-party developers. The release of such an API would unlock a new wave of innovation in Android messaging, allowing for a diverse ecosystem of competing clients. Its continued absence will cement the role of Google Messages as the sole gateway to P2P RCS on Android, pushing all other developers towards the server-side Business Messaging APIs. The resolution of this question will ultimately define the future of messaging development and user choice on the world's largest mobile operating system.

#### **Works cited**

1. GSM 03.40 \- Version 5.3.0 \- Digital cellular telecommunications system (Phase 2+); Technical realization of the Short Message Se \- ETSI, accessed August 16, 2025, [https://www.etsi.org/deliver/etsi\_gts/03/0340/05.03.00\_60/gsmts\_0340v050300p.pdf](https://www.etsi.org/deliver/etsi_gts/03/0340/05.03.00_60/gsmts_0340v050300p.pdf)  
2. SMS PDU mode, accessed August 16, 2025, [http://www.gsm-modem.de/sms-pdu-mode.html](http://www.gsm-modem.de/sms-pdu-mode.html)  
3. A Methodology for Implementation of MMS Client on Embedded Platforms \- arXiv, accessed August 16, 2025, [https://arxiv.org/pdf/1403.4158](https://arxiv.org/pdf/1403.4158)  
4. \[MS-OMS\]: Incoming Multimedia Message | Microsoft Learn, accessed August 16, 2025, [https://learn.microsoft.com/en-us/openspecs/sharepoint\_protocols/ms-oms/6e8e864a-4e63-45c4-a675-97247997859c](https://learn.microsoft.com/en-us/openspecs/sharepoint_protocols/ms-oms/6e8e864a-4e63-45c4-a675-97247997859c)  
5. Rich Communication Services \- Wikipedia, accessed August 16, 2025, [https://en.wikipedia.org/wiki/Rich\_Communication\_Services](https://en.wikipedia.org/wiki/Rich_Communication_Services)  
6. Universal Profile \- Networks \- GSMA, accessed August 16, 2025, [https://www.gsma.com/solutions-and-impact/technologies/networks/rcs/universal-profile/](https://www.gsma.com/solutions-and-impact/technologies/networks/rcs/universal-profile/)  
7. Inside Universal Profile 3.0: The New RCS Features Transforming Brand Messaging \- Dotgo, accessed August 16, 2025, [https://www.dotgo.com/blog/inside-universal-profile-3-0-the-game-changing-rcs-features-transforming-brand-messaging/](https://www.dotgo.com/blog/inside-universal-profile-3-0-the-game-changing-rcs-features-transforming-brand-messaging/)  
8. Why Is MMS Not Working on Android? 6 Ways to Fix the Problem \- Plivo, accessed August 16, 2025, [https://www.plivo.com/blog/mms-not-working-android-fix/](https://www.plivo.com/blog/mms-not-working-android-fix/)  
9. Why Are MMS Messages Not Working?, accessed August 16, 2025, [https://www.messagecentral.com/blog/why-are-mms-messages-not-working](https://www.messagecentral.com/blog/why-are-mms-messages-not-working)  
10. What is the Universal Profile? \- Sinch Community \- 16539, accessed August 16, 2025, [https://community.sinch.com/t5/RCS/What-is-the-Universal-Profile-nbsp/ta-p/16539](https://community.sinch.com/t5/RCS/What-is-the-Universal-Profile-nbsp/ta-p/16539)  
11. Difference between RCS and Universal Profile : r/UniversalProfile \- Reddit, accessed August 16, 2025, [https://www.reddit.com/r/UniversalProfile/comments/1i8cfcf/difference\_between\_rcs\_and\_universal\_profile/](https://www.reddit.com/r/UniversalProfile/comments/1i8cfcf/difference_between_rcs_and_universal_profile/)  
12. If RCS is an open standard, why are no other third party apps implementing it? \- Reddit, accessed August 16, 2025, [https://www.reddit.com/r/Android/comments/1ijojed/if\_rcs\_is\_an\_open\_standard\_why\_are\_no\_other\_third/](https://www.reddit.com/r/Android/comments/1ijojed/if_rcs_is_an_open_standard_why_are_no_other_third/)  
13. RCS Chat \- What is it and how does it work? \- Stream, accessed August 16, 2025, [https://getstream.io/glossary/rcs-chat/](https://getstream.io/glossary/rcs-chat/)  
14. Introduction to the SMS PDU and Text format \- GSM Favorites, accessed August 16, 2025, [https://www.gsmfavorites.com/documents/sms/pdutext/](https://www.gsmfavorites.com/documents/sms/pdutext/)  
15. SMS-DELIVER TPDU Structure \- Blue Security Blog \- WordPress.com, accessed August 16, 2025, [https://bluesecblog.wordpress.com/2016/11/15/sms-deliver-tpdu-structure/](https://bluesecblog.wordpress.com/2016/11/15/sms-deliver-tpdu-structure/)  
16. SMS with the SMS PDU-Mode \- Alternative Technology, accessed August 16, 2025, [http://www.alternative-technology.de/sms\_pdumode.pdf](http://www.alternative-technology.de/sms_pdumode.pdf)  
17. Building an MMS \- OpenMarket, accessed August 16, 2025, [https://www.openmarket.com/docs/Content/apis/mms/mms-building-a-message.htm](https://www.openmarket.com/docs/Content/apis/mms/mms-building-a-message.htm)  
18. How to order parts of mutlipart mms when sending programmaticaly \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/48336640/how-to-order-parts-of-mutlipart-mms-when-sending-programmaticaly](https://stackoverflow.com/questions/48336640/how-to-order-parts-of-mutlipart-mms-when-sending-programmaticaly)  
19. Description of multipart/mixed Internet message format \- Exchange \- Microsoft Learn, accessed August 16, 2025, [https://learn.microsoft.com/en-us/troubleshoot/exchange/administration/multipart-mixed-mime-message-format](https://learn.microsoft.com/en-us/troubleshoot/exchange/administration/multipart-mixed-mime-message-format)  
20. RCS Messaging API Guide \- Vonage, accessed August 16, 2025, [https://developer.vonage.com/en/messages/concepts/rcs](https://developer.vonage.com/en/messages/concepts/rcs)  
21. 2.0.0 Version 6-Feb-2002 Open Mobile Alliance OMA-IOP-MMSCONF-2\_0\_0-20020206C \- MMS Conformance Document, accessed August 16, 2025, [https://www.openmobilealliance.org/release/MMS/V1\_1-20021104-C/OMA-IOP-MMSCONF-V2\_0\_0-20020206-C.pdf](https://www.openmobilealliance.org/release/MMS/V1_1-20021104-C/OMA-IOP-MMSCONF-V2_0_0-20020206-C.pdf)  
22. Rich Communication Service (RCS) \- June 2024 Publications \- Networks \- GSMA, accessed August 16, 2025, [https://www.gsma.com/solutions-and-impact/technologies/networks/gsma\_resources/rich-communication-service-june-2024-publications/](https://www.gsma.com/solutions-and-impact/technologies/networks/gsma_resources/rich-communication-service-june-2024-publications/)  
23. RCS Universal Profile 3.0 Spec PDF Download : r/UniversalProfile \- Reddit, accessed August 16, 2025, [https://www.reddit.com/r/UniversalProfile/comments/1jb10ye/rcs\_universal\_profile\_30\_spec\_pdf\_download/](https://www.reddit.com/r/UniversalProfile/comments/1jb10ye/rcs_universal_profile_30_spec_pdf_download/)  
24. A new advancement boosts RCS audio notes and general connectivity in big ways \- Android Police, accessed August 16, 2025, [https://www.androidpolice.com/rcs-universal-profile-31-release/](https://www.androidpolice.com/rcs-universal-profile-31-release/)  
25. RCS & Universal Profile FAQs \- Networks \- GSMA, accessed August 16, 2025, [https://www.gsma.com/solutions-and-impact/technologies/networks/gsma\_resources/rcs-universal-profile-faqs/](https://www.gsma.com/solutions-and-impact/technologies/networks/gsma_resources/rcs-universal-profile-faqs/)  
26. Google RCS Messaging Explained | Openmind Networks, accessed August 16, 2025, [https://www.openmindnetworks.com/blog/google-rcs-messaging-explained/](https://www.openmindnetworks.com/blog/google-rcs-messaging-explained/)  
27. RCS Messaging \- A Comprehensive Guide \- Gupshup, accessed August 16, 2025, [https://www.gupshup.io/resources/blog/rcs-messaging-a-comprehensive-guide](https://www.gupshup.io/resources/blog/rcs-messaging-a-comprehensive-guide)  
28. class android.provider.Telephony \- The source code, accessed August 16, 2025, [http://opensource.hcltechsw.com/volt-mx-native-function-docs/Android/android.provider-Android-10.0/source/Telephony.html](http://opensource.hcltechsw.com/volt-mx-native-function-docs/Android/android.provider-Android-10.0/source/Telephony.html)  
29. android \- How to use SMS content provider? Where are the docs? \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/1976252/how-to-use-sms-content-provider-where-are-the-docs](https://stackoverflow.com/questions/1976252/how-to-use-sms-content-provider-where-are-the-docs)  
30. Android Messaging Forensics – SMS/MMS and Beyond, accessed August 16, 2025, [https://www.magnetforensics.com/blog/android-messaging-forensics-sms-mms-and-beyond/](https://www.magnetforensics.com/blog/android-messaging-forensics-sms-mms-and-beyond/)  
31. android.provider.Telephony.MmsSms \- Documentation \- HCL Software Open Source, accessed August 16, 2025, [http://opensource.hcltechsw.com/volt-mx-native-function-docs/Android/android.provider-Android-10.0/\#\!/api/android.provider.Telephony.MmsSms](http://opensource.hcltechsw.com/volt-mx-native-function-docs/Android/android.provider-Android-10.0/#!/api/android.provider.Telephony.MmsSms)  
32. src/com/android/providers/telephony/MmsSmsProvider.java \- platform/packages/providers/TelephonyProvider \- Git at Google, accessed August 16, 2025, [https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/master/src/com/android/providers/telephony/MmsSmsProvider.java](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/master/src/com/android/providers/telephony/MmsSmsProvider.java)  
33. Telephony.Sms.Inbox | API reference \- Android Developers, accessed August 16, 2025, [https://developer.android.com/reference/android/provider/Telephony.Sms.Inbox](https://developer.android.com/reference/android/provider/Telephony.Sms.Inbox)  
34. mms-common/java/com/android/common/mms/telephony/TelephonyProvider.java \- platform/frameworks/native \- Git at Google, accessed August 16, 2025, [https://android.googlesource.com/platform/frameworks/native/+/c3b9f0e/mms-common/java/com/android/common/mms/telephony/TelephonyProvider.java](https://android.googlesource.com/platform/frameworks/native/+/c3b9f0e/mms-common/java/com/android/common/mms/telephony/TelephonyProvider.java)  
35. android.provider.Telephony.Mms.Part \- Documentation \- HCL Software Open Source, accessed August 16, 2025, [http://opensource.hcltechsw.com/volt-mx-native-function-docs/Android/android.provider-Android-10.0/\#\!/api/android.provider.Telephony.Mms.Part](http://opensource.hcltechsw.com/volt-mx-native-function-docs/Android/android.provider-Android-10.0/#!/api/android.provider.Telephony.Mms.Part)  
36. Telephony.Mms | API reference \- Android Developers, accessed August 16, 2025, [https://developer.android.com/reference/android/provider/Telephony.Mms](https://developer.android.com/reference/android/provider/Telephony.Mms)  
37. Can't retrieve MMS data \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/41699481/cant-retrieve-mms-data](https://stackoverflow.com/questions/41699481/cant-retrieve-mms-data)  
38. MMS in Android. Part 2\. Working with MMS storage \- maxim bogatov \- WordPress.com, accessed August 16, 2025, [https://maximbogatov.wordpress.com/2011/08/15/mms-in-android-part-2-working-with-mms-storage/](https://maximbogatov.wordpress.com/2011/08/15/mms-in-android-part-2-working-with-mms-storage/)  
39. Broadcasts overview | Background work \- Android Developers, accessed August 16, 2025, [https://developer.android.com/develop/background-work/background-tasks/broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts)  
40. 2.2: Sending and Receiving SMS Messages \- Part 2 · GitBook, accessed August 16, 2025, [https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2\_p\_2\_sending\_sms\_messages.html](https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html)  
41. Broadcast receivers (best practices and examples) | by shareknowledge \- Medium, accessed August 16, 2025, [https://medium.com/@icodingteam/broadcast-receivers-best-practices-and-examples-f2dc2fe21c42](https://medium.com/@icodingteam/broadcast-receivers-best-practices-and-examples-f2dc2fe21c42)  
42. BroadcastReceiver \+ SMS\_RECEIVED \- android \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/1973071/broadcastreceiver-sms-received](https://stackoverflow.com/questions/1973071/broadcastreceiver-sms-received)  
43. Android BroadcastReceiver not receiving SMS broadcasts \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/77779564/android-broadcastreceiver-not-receiving-sms-broadcasts](https://stackoverflow.com/questions/77779564/android-broadcastreceiver-not-receiving-sms-broadcasts)  
44. Sending and Receiving SMS and MMS in Android (pre Kit Kat Android 4.4) \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/14452808/sending-and-receiving-sms-and-mms-in-android-pre-kit-kat-android-4-4](https://stackoverflow.com/questions/14452808/sending-and-receiving-sms-and-mms-in-android-pre-kit-kat-android-4-4)  
45. Android \- SMS Broadcast receiver \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/4117701/android-sms-broadcast-receiver](https://stackoverflow.com/questions/4117701/android-sms-broadcast-receiver)  
46. Android \- How to be the first to receive WAP PUSH (MMS) \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/28623745/android-how-to-be-the-first-to-receive-wap-push-mms](https://stackoverflow.com/questions/28623745/android-how-to-be-the-first-to-receive-wap-push-mms)  
47. Getting Your SMS Apps Ready for KitKat \- Android Developers Blog, accessed August 16, 2025, [https://android-developers.googleblog.com/2013/10/getting-your-sms-apps-ready-for-kitkat.html](https://android-developers.googleblog.com/2013/10/getting-your-sms-apps-ready-for-kitkat.html)  
48. samples/ApiDemos/src/com/example/android/apis/os/MmsWapPushReceiver.java \- platform/development \- Git at Google, accessed August 16, 2025, [https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/os/MmsWapPushReceiver.java](https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/os/MmsWapPushReceiver.java)  
49. MmsListener.java example \- Javatips.net, accessed August 16, 2025, [https://www.javatips.net/api/TextSecureSMP-master/src/org/thoughtcrime/SMP/service/MmsListener.java](https://www.javatips.net/api/TextSecureSMP-master/src/org/thoughtcrime/SMP/service/MmsListener.java)  
50. BroadcastReceiver Android. Broadcast receivers are components in… | by App Dev Insights | Medium, accessed August 16, 2025, [https://medium.com/@appdevinsights/broadcastreceiver-android-39915f6d11a7](https://medium.com/@appdevinsights/broadcastreceiver-android-39915f6d11a7)  
51. Implicit broadcast exceptions | Background work \- Android Developers, accessed August 16, 2025, [https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)  
52. Can't start activity from BroadcastReceiver on android 10 \- Stack Overflow, accessed August 16, 2025, [https://stackoverflow.com/questions/57833208/cant-start-activity-from-broadcastreceiver-on-android-10](https://stackoverflow.com/questions/57833208/cant-start-activity-from-broadcastreceiver-on-android-10)  
53. klinker41/android-smsmms: Library for easily sending SMS ... \- GitHub, accessed August 16, 2025, [https://github.com/klinker41/android-smsmms](https://github.com/klinker41/android-smsmms)  
54. SMS and MMS Text Messaging library | B4X Programming Forum, accessed August 16, 2025, [https://www.b4x.com/android/forum/threads/sms-and-mms-text-messaging-library.89988/](https://www.b4x.com/android/forum/threads/sms-and-mms-text-messaging-library.89988/)  
55. FossifyOrg/Messages: An easy and quick way of managing ... \- GitHub, accessed August 16, 2025, [https://github.com/FossifyOrg/Messages](https://github.com/FossifyOrg/Messages)  
56. adorsys/sms-parser-android: Intercept a sms in your application \- GitHub, accessed August 16, 2025, [https://github.com/adorsys/sms-parser-android](https://github.com/adorsys/sms-parser-android)  
57. android-rcs/rcsjta: RCS-e stack for Android with GSMA API \- GitHub, accessed August 16, 2025, [https://github.com/android-rcs/rcsjta](https://github.com/android-rcs/rcsjta)  
58. RCS Business Messaging \- Google for Developers, accessed August 16, 2025, [https://developers.google.com/business-communications/rcs-business-messaging](https://developers.google.com/business-communications/rcs-business-messaging)  
59. RCS Business Messaging API \- Google for Developers, accessed August 16, 2025, [https://developers.google.com/business-communications/rcs-business-messaging/reference/rest](https://developers.google.com/business-communications/rcs-business-messaging/reference/rest)  
60. google-business-communications/java-rcsbusinessmessaging: RCS Business Messaging upgrades SMS with branding, rich media, interactivity, and analytics. With RCS, businesses can bring branded, interactive mobile experiences, right to the native Android messaging app.. This library can be used to ease the development of RCS Business Messaging applications \- GitHub, accessed August 16, 2025, [https://github.com/google-business-communications/java-rcsbusinessmessaging](https://github.com/google-business-communications/java-rcsbusinessmessaging)  
61. google-business-communications/nodejs-rcsbusinessmessaging: RCS Business Messaging upgrades SMS with branding, rich media, interactivity, and analytics. With RCS, businesses can bring branded, interactive mobile experiences, right to the native Android messaging app. This library can be used to ease the development of RCS Business Messaging applications in \- GitHub, accessed August 16, 2025, [https://github.com/google-business-communications/nodejs-rcsbusinessmessaging](https://github.com/google-business-communications/nodejs-rcsbusinessmessaging)  
62. Business Communications by Google \- GitHub, accessed August 16, 2025, [https://github.com/google-business-communications](https://github.com/google-business-communications)  
63. How to fix MMS messages not downloading on Android | Twilio, accessed August 16, 2025, [https://www.twilio.com/en-us/blog/mms-messages-not-sending](https://www.twilio.com/en-us/blog/mms-messages-not-sending)  
64. How to send & receive RCS (Rich Communication Services) Messages from Android app?, accessed August 16, 2025, [https://stackoverflow.com/questions/77366833/how-to-send-receive-rcs-rich-communication-services-messages-from-android-ap](https://stackoverflow.com/questions/77366833/how-to-send-receive-rcs-rich-communication-services-messages-from-android-ap)  
65. Will google open RCS api? (2024) : r/UniversalProfile \- Reddit, accessed August 16, 2025, [https://www.reddit.com/r/UniversalProfile/comments/1914dod/will\_google\_open\_rcs\_api\_2024/](https://www.reddit.com/r/UniversalProfile/comments/1914dod/will_google_open_rcs_api_2024/)  
66. When will RCS API be released to third party developers? \- Google Messages Community, accessed August 16, 2025, [https://support.google.com/messages/thread/247624435/when-will-rcs-api-be-released-to-third-party-developers?hl=en](https://support.google.com/messages/thread/247624435/when-will-rcs-api-be-released-to-third-party-developers?hl=en)  
67. Sample of how to intercept a SMS with BroadcastReceiver \- GitHub Gist, accessed August 16, 2025, [https://gist.github.com/gbzarelli/90155562fffd14e6be411c5047b0fdff](https://gist.github.com/gbzarelli/90155562fffd14e6be411c5047b0fdff)  
68. Fix problems sending, receiving, or connecting to Google Messages \- Android, accessed August 16, 2025, [https://support.google.com/messages/answer/9077245?hl=en\&co=GENIE.Platform%3DAndroid](https://support.google.com/messages/answer/9077245?hl=en&co=GENIE.Platform%3DAndroid)  
69. How to Fix RCS (Google Messages) Not Working on Android? \- GeeksforGeeks, accessed August 16, 2025, [https://www.geeksforgeeks.org/techtips/fix-rcs-google-messages-issue-on-android/](https://www.geeksforgeeks.org/techtips/fix-rcs-google-messages-issue-on-android/)  
70. RCS Messaging Issues on Android \- Google Help, accessed August 16, 2025, [https://support.google.com/messages/thread/341476199/rcs-messaging-issues-on-android?hl=en](https://support.google.com/messages/thread/341476199/rcs-messaging-issues-on-android?hl=en)  
71. Messages is switching back and forth between RCS and SMS (iPhone and Android texting) : r/GooglePixel \- Reddit, accessed August 16, 2025, [https://www.reddit.com/r/GooglePixel/comments/1i349r3/messages\_is\_switching\_back\_and\_forth\_between\_rcs/](https://www.reddit.com/r/GooglePixel/comments/1i349r3/messages_is_switching_back_and_forth_between_rcs/)