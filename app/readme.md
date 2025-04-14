# Handwritten Notes App for Onyx E-ink Devices

An Android application for taking handwritten notes on Onyx e-ink devices. This app provides a smooth drawing experience optimized for e-ink displays, with multiple pen types, colors, and other features.

## Features

### Drawing Experience
- Multiple pen types (ballpoint, fountain, marker)
- Adjustable pen sizes and colors
- Pressure-sensitive drawing
- Eraser tool with different modes
- Smooth and responsive drawing optimized for e-ink displays
- Zoom and pan support

### Organization
- Organize notes into notebooks
- Multiple page support within each notebook
- Different page sizes (A4, Letter)
- Customizable notebook covers

### Database Structure
The app uses Room database to store notebooks, pages, and strokes:

- **Notebooks**: Collection of pages with title, creation date, and page type
- **Pages**: Individual drawing surfaces within notebooks
- **Strokes**: Vector-based pen strokes saved for perfect quality
- **Stroke Points**: Individual points that make up each stroke, preserving pressure and tilt data

## Technical Details

### Database Schema
The database consists of four main entities:
- **Notebook**: Stores metadata about notebooks
- **Page**: Represents a drawing page within a notebook
- **StrokeEntity**: Stores information about each drawing stroke
- **StrokePointEntity**: Stores the individual points that make up each stroke

### Drawing Implementation
- Uses Onyx SDK for hardware-accelerated drawing
- Direct integration with e-ink display for minimal latency
- Custom stroke rendering for different pen types

### Navigation
- Notebooks list → Notebook detail → Page editor
- Fluid navigation between pages within a notebook

## Future Features
- PDF export
- Moving pages between notebooks
- Handwriting recognition
- Cloud synchronization
- Templates and backgrounds