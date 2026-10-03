---
name: Kafi Kafi
description: A compact operator console for daily Kafka work.
colors:
  interaction: "#D4D9E1"
  graphite-background: "#191A1C"
  graphite-sidebar: "#1E1F22"
  graphite-surface: "#1E1F22"
  graphite-elevated: "#242529"
  graphite-hover: "#2B2D31"
  divider: "#3B3E44"
  text-primary: "#F4F7FA"
  text-secondary: "#B4BDC9"
  text-muted: "#929DAB"
  status-success: "#5CCB8A"
  status-warning: "#F6C177"
  status-error: "#FF7A7A"
typography:
  headline:
    fontSize: "19px"
    fontWeight: 600
    lineHeight: "26px"
  body:
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
  label:
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "16px"
rounded:
  xs: "4px"
  sm: "6px"
  md: "8px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
components:
  navigation-rail:
    backgroundColor: "{colors.graphite-sidebar}"
    width: "64px"
  tab:
    backgroundColor: "{colors.graphite-surface}"
    height: "38px"
    rounded: "{rounded.xs}"
---

# Design System: Kafi Kafi

## Overview

Kafi Kafi is a compact workspace for Kafka operators. Its neutral palette, compact controls, and separation of navigation from actions take inspiration from [bb](https://github.com/get-bb/bb). Operational state, resource names, numeric data, and the active selection determine the visual hierarchy.

**Key Characteristics:**

- compact icon navigation with accessible descriptions and tooltips;
- persistent cluster status above the workspace;
- dense tabs, lists, tables, and inspectors separated by tonal planes;
- neutral interaction states and explicit semantic status colors.

## Colors

Dark mode uses graphite surfaces and a pale primary button; light mode uses white surfaces, a pale gray sidebar, and a dark primary button. Focus and selection follow the neutral foreground color, with green, amber, and red reserved for operational meaning. Text, dividers, hover states, errors and native controls follow the selected theme.

Selected navigation, active tabs, and selected rows use a small foreground marker and a subtle background tint. Containers use quiet borders.

## Typography

Inter Variable is bundled locally for consistent rendering, with the platform sans-serif as a fallback. Sizes stay between 11px and 19px for desktop density; weight, alignment, and surface contrast create hierarchy. Numeric data uses tabular figures.

Use monospaced text only for payloads, offsets, identifiers, configuration values, and other data where character alignment matters.

## Layout

The application shell uses a 64px icon rail, a 42px cluster-status bar, a 38px tab strip, and the remaining space for the active workspace. Topic sections use a compact segmented control below the resource heading; refresh and deletion stay beside that heading. Resource-heavy screens use a resource browser, data table, and contextual inspector. Forms pair related fields where space permits. Standard spacing steps are 4, 8, 12, 16, 20, 24, and 32px.

## Elevation & Depth

The system is flat. Depth comes from adjacent surface tones and one-pixel dividers, not shadows, glow, blur, or glass.

## Shapes

Controls and compact containers use 4–8px corners. Pills are limited to small status badges. Large rounded cards and nested cards do not belong in the operator workspace.

## Components

### Navigation

The primary rail uses 42px icon buttons inside a 64px column, with consistent outline SVG icons. The active destination gains a foreground icon, a subtle tint, and a two-pixel side marker. Settings stays at the bottom. Every icon-only action has an accessible description and tooltip.

### Tabs

Tabs are 38px high with a two-pixel foreground bottom indicator for the active tab. Close actions remain compact but independently focusable.

### Buttons and inputs

Standard form controls are 32px high or taller, with six-pixel corners; status, segmented, profile-row and table actions use compact sizing. Primary actions use a filled foreground color; secondary actions use an outline or a borderless treatment. Text remains on primary, destructive, and ambiguous actions. Familiar secondary actions may use icons. Focus uses a visible two-pixel outline. Confirmation buttons name the action, such as "Delete topic" or "Import profiles". Hover transitions are brief and respect reduced-motion preferences.

### Data surfaces

Tables use left-aligned headers, 32px data rows, subtle dividers, and a single selected-row treatment. In message tables, the value preview uses the remaining flexible width, with a minimum of 100px; metadata columns have fixed widths. Inspectors sit beside the selected resource, with payloads in bounded code blocks.

## Do's and Don'ts

### Do:

- **Do** keep cluster and operation status visible near the affected workspace.
- **Do** use consistent outline SVG icons for routine actions and expose their names on hover.
- **Do** keep destructive actions explicit and labelled.

### Don't:

- **Don't** add explanatory copy when placement, iconography, and tooltip already communicate the action.
- **Don't** use gradients, glow, glass, decorative dashboards, or oversized metric cards.
- **Don't** introduce a new color when an existing interaction or status role already fits.


## Runtime tokens

The React interface implements these colors as CSS custom properties in src/styles/global.css. The `data-theme` attribute on the document selects dark or light tokens; the choice is stored in the application settings as `layout.theme`. Virtual tables and adjacent inspectors carry Kafka data; the interface stores bounded row previews and requests complete records from Rust only on selection. Tauri/Rust/React is the only application implementation.
