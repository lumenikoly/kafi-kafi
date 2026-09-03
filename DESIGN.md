---
name: Light Kafka
description: A compact operator console for daily Kafka work.
colors:
  cobalt-focus: "#5B8CFF"
  graphite-background: "#0B0D10"
  graphite-sidebar: "#0F1217"
  graphite-surface: "#14181F"
  graphite-elevated: "#1B2028"
  graphite-hover: "#232A34"
  divider: "#252B34"
  text-primary: "#F4F7FA"
  text-secondary: "#B4BDC9"
  text-muted: "#7F8998"
  status-success: "#5CCB8A"
  status-warning: "#F6C177"
  status-error: "#FF7A7A"
typography:
  headline:
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
  body:
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "18sp"
  label:
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
rounded:
  xs: "4dp"
  sm: "6dp"
  md: "8dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  navigation-rail:
    backgroundColor: "{colors.graphite-sidebar}"
    width: "64dp"
  tab:
    backgroundColor: "{colors.graphite-surface}"
    height: "38dp"
    rounded: "{rounded.xs}"
---

# Design System: Light Kafka

## Overview

**Creative North Star: "Operator Console"**

Light Kafka is a quiet, high-density technical workspace. It borrows the speed and spatial discipline of database IDEs without copying their visual noise. Operational state, resource names, numeric data, and the active selection always outrank decoration.

**Key Characteristics:**

- compact icon navigation with accessible descriptions and tooltips;
- persistent cluster status above the workspace;
- dense tabs, lists, tables, and inspectors separated by tonal planes;
- one cobalt interaction color and explicit semantic status colors.

## Colors

Graphite surfaces reduce glare during long sessions. Cobalt marks focus and selection; mint, amber, and red are reserved for operational meaning.

**The Sparse Accent Rule.** Cobalt identifies the current context or primary action. It does not decorate containers.

## Typography

The platform sans-serif remains the UI workhorse. Sizes stay between 11sp and 20sp for desktop density; weight, alignment, and surface contrast create hierarchy.

Use monospaced text only for payloads, offsets, identifiers, configuration values, and other data where character alignment matters.

## Layout

The application shell uses a 64dp icon rail, a 40dp cluster-status bar, a 38dp tab strip, and the remaining space for the active workspace. Resource-heavy screens should prefer a resource browser, data table, and contextual inspector over dashboards or repeated cards. Standard spacing steps are 4, 8, 12, 16, and 24dp.

## Elevation & Depth

The system is flat. Depth comes from adjacent graphite tones and one-pixel dividers, not shadows, glow, blur, or glass.

## Shapes

Controls and compact containers use 4–8dp corners. Pills are limited to small status badges. Large rounded cards and nested cards do not belong in the operator workspace.

## Components

### Navigation

The primary rail uses 44dp icon buttons inside a 64dp column. The active destination gains a darker surface and cobalt icon. Every icon-only action has an accessible description and tooltip.

### Tabs

Tabs are 38dp high with a two-pixel cobalt top indicator for the active tab. Close actions remain compact but independently focusable.

### Buttons and inputs

Text remains on primary, destructive, and ambiguous actions. Familiar secondary actions may use icons. Inputs use subtle default borders and a cobalt focus border.

### Data surfaces

Tables and lists use aligned columns, compact rows, subtle dividers, and a single selected-row treatment. Inspectors sit beside or below the selected resource instead of opening a modal.

## Do's and Don'ts

### Do:

- **Do** keep cluster and operation status visible near the affected workspace.
- **Do** use familiar Material icons for routine actions and expose their names on hover.
- **Do** keep destructive actions explicit and labelled.

### Don't:

- **Don't** add explanatory copy when placement, iconography, and tooltip already communicate the action.
- **Don't** use gradients, glow, glass, decorative dashboards, or oversized metric cards.
- **Don't** introduce a new color when an existing interaction or status role already fits.
