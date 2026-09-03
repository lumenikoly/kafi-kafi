# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

Light Kafka is primarily for developers and DevOps engineers who inspect and operate Kafka clusters during daily development and operational work.

## Product Purpose

Light Kafka provides a local desktop workspace for connecting directly to Kafka, inspecting cluster state, browsing and producing messages, and managing consumer groups without a separate backend.

## Positioning

The application keeps profiles, credentials, and cluster access on the user's computer while combining common Kafka inspection and operational tasks in one native workspace.

## Operating Context

Users work with dense technical data for extended sessions. Fast scanning, clear connection and operation status, keyboard-friendly controls, and efficient use of desktop space take priority over explanatory decoration.

## Capabilities and Constraints

- The application connects directly to external Kafka clusters through the Kafka Java client.
- It supports topic, broker, message, consumer-group, connection, settings, and optional local KRaft container workflows.
- Destructive and high-impact actions must remain explicit even when routine actions use icons.
- Icons may replace text only when their meaning is conventional or exposed through an accessible description and tooltip.
- The interface is implemented with Compose Multiplatform for desktop and packaged for Linux, Windows, and macOS.

## Brand Commitments

The product name is Light Kafka. Its voice is concise, technical, and calm.

## Evidence on Hand

Current capabilities and system boundaries are documented in `README.md` and `docs/`. No logo, custom typeface, marketing imagery, or formal visual identity is present in the repository.

## Product Principles

- Show operational state before secondary detail.
- Keep dense data readable and actions close to their object.
- Prefer recognition through familiar desktop conventions over explanatory copy.
- Preserve user control around destructive or irreversible operations.
- Keep the workspace quiet enough for long technical sessions.

## Accessibility & Inclusion

Icon-only actions require accessible descriptions and visible tooltips. Focus order must follow the primary workflow, and color must not be the only signal for status.
