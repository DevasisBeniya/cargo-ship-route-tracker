/**
 * Cargo Ship Route and Load Tracker
 * Flagship Vessel: MV Pirates Voyager
 * Frontend Engine: Leaflet.js + OpenStreetMap (No Google Maps, No API keys)
 */

document.addEventListener('DOMContentLoaded', () => {
    // --- Application State ---
    const state = {
        allPorts: [],
        startPortId: 'IN_BOM',
        selectedDestIds: new Set(),
        shipCapacity: 24000,
        cargoAllocations: {}, // portId -> boxes
        currentRouteData: null,
        legs: [],            // one entry per leg of the voyage (stop N to stop N+1)
        selectedLeg: null,   // index of the leg the user clicked (null = all legs visible)
        legLayer: null,      // Leaflet layer group that holds all leg lines, arrows and labels
        
        // Simulation State
        animation: {
            isRunning: false,
            isPaused: false,
            speedMultiplier: 1,
            routeCoordinates: [], // Array of [lat, lon]
            stopIndices: [],      // Coordinate indices matching each stop
            currentStep: 0,
            shipIndex: 0,         // route coordinate index where the ship marker was last drawn
            animFrameId: null,
            deliveredBoxes: 0,
            remainingBoxes: 24000,
            shipMarker: null,
            routePolyline: null,
            portMarkers: []
        }
    };

    // --- DOM Element References ---
    const dom = {
        startPortSelect: document.getElementById('startPortSelect'),
        destPortsContainer: document.getElementById('destPortsContainer'),
        destCounterBadge: document.getElementById('destCounterBadge'),
        portSearchInput: document.getElementById('portSearchInput'),
        presetDemoBtn: document.getElementById('presetDemoBtn'),
        clearDestsBtn: document.getElementById('clearDestsBtn'),
        
        shipCapacityInput: document.getElementById('shipCapacityInput'),
        resetCapacityBtn: document.getElementById('resetCapacityBtn'),
        
        cargoAllocationInputs: document.getElementById('cargoAllocationInputs'),
        autoBalanceBtn: document.getElementById('autoBalanceBtn'),
        cargoBalanceBadge: document.getElementById('cargoBalanceBadge'),
        totalAssignedText: document.getElementById('totalAssignedText'),
        allocationProgressBar: document.getElementById('allocationProgressBar'),
        cargoValidationMessage: document.getElementById('cargoValidationMessage'),
        calculateRouteBtn: document.getElementById('calculateRouteBtn'),
        
        voyageExecutionCard: document.getElementById('voyageExecutionCard'),
        voyageStatusBadge: document.getElementById('voyageStatusBadge'),
        metricDistance: document.getElementById('metricDistance'),
        metricDistanceKm: document.getElementById('metricDistanceKm'),
        metricStops: document.getElementById('metricStops'),
        metricDelivered: document.getElementById('metricDelivered'),
        metricRemaining: document.getElementById('metricRemaining'),
        deliveryProgressBar: document.getElementById('deliveryProgressBar'),
        deliveryPercentageText: document.getElementById('deliveryPercentageText'),
        voyageLiveText: document.getElementById('voyageLiveText'),
        
        startVoyageBtn: document.getElementById('startVoyageBtn'),
        pauseVoyageBtn: document.getElementById('pauseVoyageBtn'),
        resetVoyageBtn: document.getElementById('resetVoyageBtn'),
        speedButtons: document.querySelectorAll('.btn-speed'),
        stopsTableBody: document.getElementById('stopsTableBody'),

        // Route legs list and map legend
        legsList: document.getElementById('legsList'),
        showAllLegsBtn: document.getElementById('showAllLegsBtn'),
        legendToggleBtn: document.getElementById('legendToggleBtn'),
        legendBody: document.getElementById('legendBody'),
        legendChevron: document.getElementById('legendChevron'),
        
        toastContainer: document.getElementById('toastContainer'),
        
        // Custom Port Modal
        openCustomPortModalBtn: document.getElementById('openCustomPortModalBtn'),
        customPortModal: document.getElementById('customPortModal'),
        closeCustomPortModalBtn: document.getElementById('closeCustomPortModalBtn'),
        cancelCustomPortBtn: document.getElementById('cancelCustomPortBtn'),
        customPortForm: document.getElementById('customPortForm'),
        customPortName: document.getElementById('customPortName'),
        customCountry: document.getElementById('customCountry'),
        customLatitude: document.getElementById('customLatitude'),
        customLongitude: document.getElementById('customLongitude'),
        customPortModalError: document.getElementById('customPortModalError')
    };

    // --- Leaflet Map Initialization ---
    // Center initially on the Indian Ocean / Asia maritime corridor
    const map = L.map('map', {
        center: [15.0, 75.0],
        zoom: 3,
        minZoom: 2,
        maxZoom: 18,
        worldCopyJump: true
    });

    // OpenStreetMap standard tiles (Open, free, no API key)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);

    // All leg lines, arrows and labels live in this layer group
    state.legLayer = L.layerGroup().addTo(map);

    // Legs are shifted a few screen pixels sideways so legs that share the same sea
    // stay visible side by side. That shift depends on the zoom, so redraw after zooming.
    map.on('zoomend', () => {
        if (state.legs.length > 0) {
            drawLegs();
        }
    });

    // Click anywhere on map to add custom port
    map.on('click', (e) => {
        const lat = e.latlng.lat.toFixed(4);
        const lon = e.latlng.lng.toFixed(4);
        
        const popupContent = document.createElement('div');
        popupContent.innerHTML = `
            <div class="popup-title">Ocean Coordinates</div>
            <div class="popup-meta">
                Lat: <strong>${lat}</strong>, Lon: <strong>${lon}</strong>
            </div>
            <button class="btn btn-xs btn-primary" style="margin-top:8px; width:100%;" id="popupAddPortBtn">
                📍 Add as Custom Port
            </button>
        `;
        
        const popup = L.popup()
            .setLatLng(e.latlng)
            .setContent(popupContent)
            .openOn(map);

        setTimeout(() => {
            const btn = document.getElementById('popupAddPortBtn');
            if (btn) {
                btn.onclick = () => {
                    map.closePopup();
                    openCustomPortModal(lat, lon);
                };
            }
        }, 100);
    });

    // --- SVG Ship Icon Generator for MV Pirates Voyager ---
    function createShipIcon(headingAngle = 0) {
        // High-contrast container cargo vessel SVG
        const svgHtml = `
            <div class="ship-voyager-marker" style="transform: rotate(${headingAngle}deg);">
                <svg class="ship-svg" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <!-- Vessel Hull -->
                    <path d="M50 5 L70 30 L65 85 L50 95 L35 85 L30 30 Z" fill="#003566" stroke="#00d2ff" stroke-width="3"/>
                    <!-- Bridge / Superstructure -->
                    <rect x="42" y="65" width="16" height="15" rx="3" fill="#ffffff" stroke="#ffd166" stroke-width="1.5"/>
                    <rect x="46" y="70" width="8" height="4" fill="#00d2ff"/>
                    <!-- Container Stacks (Teal & Gold TEUs) -->
                    <rect x="37" y="32" width="11" height="8" rx="1" fill="#ffb703"/>
                    <rect x="52" y="32" width="11" height="8" rx="1" fill="#06d6a0"/>
                    <rect x="37" y="42" width="11" height="8" rx="1" fill="#00b4d8"/>
                    <rect x="52" y="42" width="11" height="8" rx="1" fill="#ef476f"/>
                    <rect x="37" y="52" width="11" height="8" rx="1" fill="#06d6a0"/>
                    <rect x="52" y="52" width="11" height="8" rx="1" fill="#ffb703"/>
                    <!-- Bow Radar / Forward Pointer -->
                    <circle cx="50" cy="18" r="3" fill="#00d2ff"/>
                </svg>
            </div>
        `;
        return L.divIcon({
            html: svgHtml,
            className: 'custom-ship-leaflet-icon',
            iconSize: [44, 44],
            iconAnchor: [22, 22]
        });
    }

    // Numbered Pin Marker Icon
    function createNumberedPin(number, isOrigin, isFinal) {
        let pinClass = 'dest-pin';
        if (isOrigin) pinClass = 'origin-pin';
        else if (isFinal) pinClass = 'final-pin';

        const html = `
            <div class="numbered-pin ${pinClass}">
                <span>${number}</span>
            </div>
        `;
        return L.divIcon({
            html: html,
            className: 'custom-port-pin',
            iconSize: [32, 32],
            iconAnchor: [16, 32],
            popupAnchor: [0, -32]
        });
    }

    // ======================================================================
    // Route legs: every leg (stop N to stop N+1) is drawn in its own colour,
    // with arrowheads that show the direction the ship sails.
    // ======================================================================
    const LEG_COLORS = ['#e63946', '#f4a100', '#12a150', '#7b2cbf', '#0077b6', '#c2185b'];
    const EARTH_RADIUS_NM = 3440.065;

    function escapeHtml(text) {
        return String(text).replace(/[&<>"']/g, ch => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[ch]));
    }

    // Great-circle distance in nautical miles between two [lat, lon] points
    function haversineNm(a, b) {
        const rad = Math.PI / 180;
        const dLat = (b[0] - a[0]) * rad;
        const dLon = (b[1] - a[1]) * rad;
        const h = Math.sin(dLat / 2) ** 2 +
                  Math.cos(a[0] * rad) * Math.cos(b[0] * rad) * Math.sin(dLon / 2) ** 2;
        return 2 * EARTH_RADIUS_NM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    // Running total distance (NM) along a list of [lat, lon] points
    function cumulativeNm(points) {
        const cum = [0];
        for (let i = 1; i < points.length; i++) {
            cum.push(cum[i - 1] + haversineNm(points[i - 1], points[i]));
        }
        return cum;
    }

    // The [lat, lon] point that lies s nautical miles along the path
    function pointAtDistance(points, cum, s) {
        const last = points.length - 1;
        if (s <= 0) return points[0];
        if (s >= cum[last]) return points[last];
        let lo = 0;
        let hi = last;
        while (hi - lo > 1) {
            const mid = (lo + hi) >> 1;
            if (cum[mid] <= s) lo = mid; else hi = mid;
        }
        const span = cum[hi] - cum[lo];
        const t = span > 0 ? (s - cum[lo]) / span : 0;
        return [
            points[lo][0] + (points[hi][0] - points[lo][0]) * t,
            points[lo][1] + (points[hi][1] - points[lo][1]) * t
        ];
    }

    // Direction on the screen (degrees, 0 = pointing right) from point a to point b
    function screenAngleDeg(a, b) {
        const pa = map.project(L.latLng(a[0], a[1]), 6);
        const pb = map.project(L.latLng(b[0], b[1]), 6);
        return Math.atan2(pb.y - pa.y, pb.x - pa.x) * 180 / Math.PI;
    }

    // Arrowhead icon that points in the sailing direction
    function createArrowIcon(color, angleDeg) {
        const html = `
            <div class="leg-arrow" style="transform: rotate(${angleDeg.toFixed(1)}deg);">
                <svg viewBox="0 0 20 20" width="20" height="20" xmlns="http://www.w3.org/2000/svg">
                    <path d="M3 3 L18 10 L3 17 L7 10 Z" fill="${color}" stroke="#ffffff" stroke-width="1.5" stroke-linejoin="round"/>
                </svg>
            </div>`;
        return L.divIcon({ html: html, className: 'leg-arrow-icon', iconSize: [20, 20], iconAnchor: [10, 10] });
    }

    // Small coloured label such as "Leg 1 · 2,345 NM"
    function createLegLabelIcon(color, text) {
        return L.divIcon({
            html: `<div class="leg-label" style="background:${color};">${escapeHtml(text)}</div>`,
            className: 'leg-label-icon',
            iconSize: [0, 0]
        });
    }

    // Move a leg a few screen pixels to the right of the sailing direction.
    // Legs that share the same sea then appear side by side and not on top of each other.
    // The shift fades to zero near the ports so every leg still starts and ends at its port.
    function offsetLegPoints(coords, lanePx) {
        const z = map.getZoom();
        const n = coords.length;
        const pts = coords.map(c => map.project(L.latLng(c[0], c[1]), z));
        const cum = cumulativeNm(coords);
        const total = cum[n - 1] || 1;
        const taperNm = Math.max(1, Math.min(120, total / 3));
        const reach = 2;

        return pts.map((p, i) => {
            const a = pts[Math.max(0, i - reach)];
            const b = pts[Math.min(n - 1, i + reach)];
            const dx = b.x - a.x;
            const dy = b.y - a.y;
            const len = Math.hypot(dx, dy);
            if (len < 1e-6) return map.unproject(p, z);
            const fade = Math.min(1, cum[i] / taperNm, (total - cum[i]) / taperNm);
            // Screen y points down, so (-dy, dx) is the right-hand side of the travel direction
            const nx = -dy / len;
            const ny = dx / len;
            return map.unproject(L.point(p.x + nx * lanePx * fade, p.y + ny * lanePx * fade), z);
        });
    }

    // Split the full route into legs, using the position of each stop along the path
    function buildLegs(data) {
        const stops = data.stops;
        const coords = state.animation.routeCoordinates;
        const stopIdx = state.animation.stopIndices;
        const lastIdx = coords.length - 1;
        const segments = (data.route && Array.isArray(data.route.segments)) ? data.route.segments : null;

        state.legs = [];
        state.selectedLeg = null;

        for (let i = 0; i < stops.length - 1; i++) {
            const startIdx = Math.min(stopIdx[i], lastIdx);
            const endIdx = Math.min(lastIdx, Math.max(stopIdx[i + 1], startIdx + 1));
            const legCoords = coords.slice(startIdx, endIdx + 1);

            // Prefer the distance from the server; otherwise measure the drawn path
            let distanceNm = null;
            if (segments && segments.length === stops.length - 1 && segments[i]) {
                const seg = segments[i];
                const fromServer = [seg.distanceNm, seg.distanceNM, seg.nauticalMiles]
                    .find(v => typeof v === 'number' && isFinite(v));
                if (fromServer !== undefined) distanceNm = fromServer;
            }
            if (distanceNm === null) {
                const cum = cumulativeNm(legCoords);
                distanceNm = cum[cum.length - 1] || 0;
            }

            state.legs.push({
                index: i,
                number: i + 1,
                from: stops[i],
                to: stops[i + 1],
                startIdx: startIdx,
                endIdx: endIdx,
                coords: legCoords,
                distanceNm: distanceNm,
                color: LEG_COLORS[i % LEG_COLORS.length],
                layers: null
            });
        }
    }

    // Draw (or redraw) every leg on the map: dashed planned line, solid sailed line,
    // direction arrows and a label in the middle of the leg.
    function drawLegs() {
        state.legLayer.clearLayers();

        state.legs.forEach(leg => {
            leg.layers = null;
            if (leg.coords.length < 2) return;

            const lanePx = 4 + (leg.index % 3) * 3.5;
            const latlngs = offsetLegPoints(leg.coords, lanePx);
            const pts = latlngs.map(p => [p.lat, p.lng]);
            const cum = cumulativeNm(pts);
            const total = cum[cum.length - 1];
            const nmText = Math.round(leg.distanceNm).toLocaleString();

            // Dashed line = the whole leg (still to sail)
            const planned = L.polyline(latlngs, {
                color: leg.color,
                weight: 4,
                opacity: 0.7,
                dashArray: '9, 8',
                lineCap: 'butt',
                bubblingMouseEvents: false
            }).addTo(state.legLayer);
            planned.bindTooltip(
                `Leg ${leg.number}: ${escapeHtml(leg.from.name)} to ${escapeHtml(leg.to.name)} (${nmText} NM)`,
                { sticky: true, className: 'leg-tooltip' }
            );
            planned.on('click', () => selectLeg(leg.index));
            planned.on('mouseover', () => planned.setStyle({ weight: 6, opacity: 0.95 }));
            planned.on('mouseout', () => applyLegStyles());

            // Solid line = the part already sailed (filled in while the ship moves)
            const sailed = L.polyline([], {
                color: leg.color,
                weight: 5,
                opacity: 1,
                lineCap: 'round',
                interactive: false
            }).addTo(state.legLayer);

            // Arrowheads spread along the leg (always an even number, so none sits under the label)
            const arrows = [];
            const arrowCount = Math.max(2, Math.min(10, 2 * Math.round(total / 900)));
            const delta = Math.max(1, Math.min(25, total * 0.02));
            for (let k = 0; k < arrowCount; k++) {
                const s = total * (k + 0.5) / arrowCount;
                const pos = pointAtDistance(pts, cum, s);
                const angle = screenAngleDeg(
                    pointAtDistance(pts, cum, s - delta),
                    pointAtDistance(pts, cum, s + delta)
                );
                arrows.push(L.marker(pos, {
                    icon: createArrowIcon(leg.color, angle),
                    interactive: false,
                    keyboard: false,
                    zIndexOffset: -1000
                }).addTo(state.legLayer));
            }

            // Label in the middle of the leg
            const label = L.marker(pointAtDistance(pts, cum, total / 2), {
                icon: createLegLabelIcon(leg.color, `Leg ${leg.number} · ${nmText} NM`),
                keyboard: false,
                zIndexOffset: -500
            }).addTo(state.legLayer);
            label.on('click', () => selectLeg(leg.index));

            leg.layers = { planned, sailed, arrows, label, latlngs, sailedCount: -1 };
        });

        applyLegStyles();
        updateSailedProgress();
    }

    // Highlight the selected leg and fade the others
    function applyLegStyles() {
        const sel = state.selectedLeg;
        state.legs.forEach(leg => {
            if (!leg.layers) return;
            const focus = sel === leg.index;
            const dim = sel !== null && !focus;
            leg.layers.planned.setStyle({ opacity: dim ? 0.12 : (focus ? 0.95 : 0.7), weight: focus ? 6 : 4 });
            leg.layers.sailed.setStyle({ opacity: dim ? 0.15 : 1, weight: focus ? 7 : 5 });
            leg.layers.arrows.forEach(m => m.setOpacity(dim ? 0.15 : 1));
            leg.layers.label.setOpacity(dim ? 0.2 : 1);
        });

        dom.legsList.querySelectorAll('.leg-item').forEach(el => {
            el.classList.toggle('selected', sel !== null && Number(el.dataset.leg) === sel);
        });
        dom.showAllLegsBtn.classList.toggle('active', sel === null);
    }

    // Click on a leg (map, label or list): show only that leg. Click again: show all legs.
    function selectLeg(index) {
        if (state.legs.length === 0) return;
        state.selectedLeg = (index === state.selectedLeg) ? null : index;
        applyLegStyles();

        if (state.selectedLeg === null) {
            fitAllLegs();
            return;
        }
        const leg = state.legs[state.selectedLeg];
        if (leg && leg.layers) {
            map.fitBounds(leg.layers.planned.getBounds(), { padding: [70, 70], maxZoom: 7 });
        }
    }

    function showAllLegs() {
        state.selectedLeg = null;
        applyLegStyles();
        fitAllLegs();
    }

    function fitAllLegs() {
        const coords = state.animation.routeCoordinates;
        if (coords.length > 0) {
            map.fitBounds(L.latLngBounds(coords), { padding: [60, 60] });
        }
    }

    // Fill in the solid "already sailed" line and the Waiting / Sailing / Done text of each leg
    function updateSailedProgress() {
        const shipIdx = state.animation.shipIndex || 0;

        state.legs.forEach(leg => {
            if (!leg.layers) return;

            const upTo = Math.min(shipIdx, leg.endIdx);
            const count = Math.max(0, upTo - leg.startIdx + 1);
            if (leg.layers.sailedCount !== count) {
                leg.layers.sailedCount = count;
                leg.layers.sailed.setLatLngs(count >= 2 ? leg.layers.latlngs.slice(0, count) : []);
            }

            let text = 'Waiting';
            let cls = 'leg-state';
            if (shipIdx >= leg.endIdx - 2 && shipIdx > leg.startIdx) {
                text = 'Done ✓';
                cls = 'leg-state done';
            } else if (shipIdx > leg.startIdx) {
                text = 'Sailing';
                cls = 'leg-state sailing';
            }
            const el = document.getElementById(`legState_${leg.index}`);
            if (el && el.textContent !== text) {
                el.textContent = text;
                el.className = cls;
            }
        });
    }

    // "Route Legs" list in the side panel (same colours as the map)
    function renderLegsPanel() {
        dom.legsList.innerHTML = '';
        state.legs.forEach(leg => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'leg-item';
            btn.dataset.leg = String(leg.index);
            btn.innerHTML = `
                <span class="leg-swatch" style="background:${leg.color};"></span>
                <span class="leg-text">
                    <span class="leg-title">Leg ${leg.number}</span>
                    <span class="leg-route">${escapeHtml(leg.from.name)} → ${escapeHtml(leg.to.name)}</span>
                </span>
                <span class="leg-side">
                    <span class="leg-nm">${Math.round(leg.distanceNm).toLocaleString()} NM</span>
                    <span class="leg-state" id="legState_${leg.index}">Waiting</span>
                </span>
            `;
            btn.addEventListener('click', () => selectLeg(leg.index));
            dom.legsList.appendChild(btn);
        });
    }

    // --- Fetch Ports Catalog ---
    async function fetchPorts() {
        try {
            const res = await fetch('/api/ports');
            if (!res.ok) throw new Error('Failed to load seaport data');
            state.allPorts = await res.json();
            renderPortSelectors();
            showToast('Loaded ' + state.allPorts.length + ' global commercial ports.', 'info');
        } catch (err) {
            console.error(err);
            showToast('Error loading ports: ' + err.message, 'error');
        }
    }

    // --- Render Start Port & Destination Lists Grouped by Country ---
    function renderPortSelectors() {
        // Group ports by country
        const groups = {};
        state.allPorts.forEach(port => {
            if (!groups[port.country]) {
                groups[port.country] = [];
            }
            groups[port.country].push(port);
        });

        const sortedCountries = Object.keys(groups).sort((a, b) => a.localeCompare(b));

        // 1. Populate Start Port Select
        dom.startPortSelect.innerHTML = '';
        sortedCountries.forEach(country => {
            const optgroup = document.createElement('optgroup');
            optgroup.label = country;
            groups[country].forEach(p => {
                const opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = `${p.name} (${country})`;
                if (p.id === state.startPortId) {
                    opt.selected = true;
                }
                optgroup.appendChild(opt);
            });
            dom.startPortSelect.appendChild(optgroup);
        });

        // 2. Populate Destination Ports Checkboxes
        renderDestinationsList(groups, sortedCountries);
        
        // Initial sync of cargo inputs
        syncCargoInputs();
    }

    function renderDestinationsList(groups, sortedCountries, filterText = '') {
        dom.destPortsContainer.innerHTML = '';
        const search = (filterText || '').toLowerCase().trim();

        let visibleCount = 0;

        sortedCountries.forEach(country => {
            const matchingPorts = groups[country].filter(p => {
                if (p.id === state.startPortId) return false; // Start port cannot be in destinations
                if (!search) return true;
                return p.name.toLowerCase().includes(search) || country.toLowerCase().includes(search);
            });

            if (matchingPorts.length === 0) return;

            const groupDiv = document.createElement('div');
            groupDiv.className = 'country-group';

            const title = document.createElement('div');
            title.className = 'country-group-title';
            title.textContent = country;
            groupDiv.appendChild(title);

            matchingPorts.forEach(port => {
                visibleCount++;
                const item = document.createElement('label');
                item.className = 'port-checkbox-item';

                const isChecked = state.selectedDestIds.has(port.id);
                const isMaxReached = state.selectedDestIds.size >= 5 && !isChecked;

                if (isMaxReached) {
                    item.classList.add('disabled');
                }

                item.innerHTML = `
                    <input type="checkbox" value="${port.id}" ${isChecked ? 'checked' : ''} ${isMaxReached ? 'disabled' : ''}>
                    <span>${port.name}</span>
                `;

                const checkbox = item.querySelector('input');
                checkbox.addEventListener('change', (e) => {
                    if (e.target.checked) {
                        if (state.selectedDestIds.size < 5) {
                            state.selectedDestIds.add(port.id);
                        } else {
                            e.target.checked = false;
                        }
                    } else {
                        state.selectedDestIds.delete(port.id);
                    }
                    onDestinationsChanged();
                });

                groupDiv.appendChild(item);
            });

            dom.destPortsContainer.appendChild(groupDiv);
        });

        if (visibleCount === 0) {
            dom.destPortsContainer.innerHTML = '<div class="loading-state">No matching ports found.</div>';
        }
    }

    function onDestinationsChanged() {
        const count = state.selectedDestIds.size;
        dom.destCounterBadge.textContent = `${count} / 5 Destinations`;
        
        if (count >= 2 && count <= 5) {
            dom.destCounterBadge.className = 'badge badge-success';
        } else {
            dom.destCounterBadge.className = 'badge badge-primary';
        }

        // Re-render destinations to update disabled state if at 5
        const groups = {};
        state.allPorts.forEach(p => {
            if (!groups[p.country]) groups[p.country] = [];
            groups[p.country].push(p);
        });
        renderDestinationsList(groups, Object.keys(groups).sort(), dom.portSearchInput.value);

        // Sync cargo allocation fields
        syncCargoInputs();
    }

    // --- Dynamic Cargo Allocation Section ---
    function syncCargoInputs() {
        const startPort = state.allPorts.find(p => p.id === state.startPortId);
        const selectedDests = state.allPorts.filter(p => state.selectedDestIds.has(p.id));

        if (!startPort || selectedDests.length < 2) {
            dom.cargoAllocationInputs.innerHTML = `
                <div class="empty-state-notice">
                    Select a start port and between 2 and 5 destination ports above to assign cargo unloads.
                </div>
            `;
            updateCargoValidation();
            return;
        }

        const allStops = [startPort, ...selectedDests];
        dom.cargoAllocationInputs.innerHTML = '';

        allStops.forEach((port, idx) => {
            const isOrigin = idx === 0;
            const row = document.createElement('div');
            row.className = `cargo-input-row ${isOrigin ? 'origin-row' : 'dest-row'}`;

            // Existing value or 0
            const currentVal = state.cargoAllocations[port.id] !== undefined 
                ? state.cargoAllocations[port.id] 
                : (isOrigin ? 0 : Math.floor(state.shipCapacity / selectedDests.length));

            state.cargoAllocations[port.id] = currentVal;

            row.innerHTML = `
                <div class="cargo-port-info">
                    <span class="cargo-port-name">${isOrigin ? '⚓ ' : (idx + 1) + '. '}${port.name}</span>
                    <span class="cargo-port-country">${port.country} ${isOrigin ? '(Departure Port)' : ''}</span>
                </div>
                <input type="number" 
                       class="form-input cargo-input-field" 
                       data-port-id="${port.id}" 
                       value="${currentVal}" 
                       min="0" 
                       max="${state.shipCapacity}" 
                       step="100">
            `;

            const input = row.querySelector('.cargo-input-field');
            input.addEventListener('input', (e) => {
                const val = parseInt(e.target.value) || 0;
                state.cargoAllocations[port.id] = Math.max(0, val);
                updateCargoValidation();
            });

            dom.cargoAllocationInputs.appendChild(row);
        });

        updateCargoValidation();
    }

    // Auto-balance button: Distributes load evenly
    function autoBalanceCargo() {
        const startPort = state.allPorts.find(p => p.id === state.startPortId);
        const selectedDests = state.allPorts.filter(p => state.selectedDestIds.has(p.id));
        if (!startPort || selectedDests.length < 2) return;

        const totalStops = 1 + selectedDests.length;
        // User adjustment #1: Start port can also unload boxes!
        // Let's give start port a realistic portion (e.g. 2,000 or equal share)
        const startPortShare = 2000;
        const remainder = state.shipCapacity - startPortShare;
        const perDest = Math.floor(remainder / selectedDests.length);
        const destRemainder = remainder - (perDest * selectedDests.length);

        state.cargoAllocations[startPort.id] = startPortShare;
        selectedDests.forEach((dest, i) => {
            // Add remainder to last stop so sum exactly equals capacity
            state.cargoAllocations[dest.id] = (i === selectedDests.length - 1) ? (perDest + destRemainder) : perDest;
        });

        // Update input field values
        document.querySelectorAll('.cargo-input-field').forEach(input => {
            const pId = input.getAttribute('data-port-id');
            if (state.cargoAllocations[pId] !== undefined) {
                input.value = state.cargoAllocations[pId];
            }
        });

        updateCargoValidation();
        showToast('Auto-balanced ' + state.shipCapacity.toLocaleString() + ' boxes across all stops.', 'info');
    }

    // Cargo Balance Validation
    function updateCargoValidation() {
        const startPort = state.allPorts.find(p => p.id === state.startPortId);
        const selectedDests = state.allPorts.filter(p => state.selectedDestIds.has(p.id));
        const allStops = startPort ? [startPort, ...selectedDests] : selectedDests;

        let totalAssigned = 0;
        allStops.forEach(p => {
            totalAssigned += (state.cargoAllocations[p.id] || 0);
        });

        const target = state.shipCapacity;
        dom.totalAssignedText.textContent = `${totalAssigned.toLocaleString()} / ${target.toLocaleString()}`;
        
        const pct = Math.min(100, Math.round((totalAssigned / target) * 100));
        dom.allocationProgressBar.style.width = `${pct}%`;

        const hasValidDestCount = selectedDests.length >= 2 && selectedDests.length <= 5;

        if (totalAssigned === target) {
            dom.cargoBalanceBadge.textContent = 'Balanced ✓';
            dom.cargoBalanceBadge.className = 'badge badge-success';
            dom.allocationProgressBar.className = 'progress-bar-fill fill-success';
            dom.cargoValidationMessage.innerHTML = `<span class="success-feedback">✓ Perfect: exactly ${target.toLocaleString()} boxes allocated across stops.</span>`;
            
            // Enable calculate button if port counts are valid
            dom.calculateRouteBtn.disabled = !hasValidDestCount;
        } else {
            dom.calculateRouteBtn.disabled = true;
            const diff = target - totalAssigned;
            if (diff > 0) {
                dom.cargoBalanceBadge.textContent = `${diff.toLocaleString()} Left`;
                dom.cargoBalanceBadge.className = 'badge badge-warning';
                dom.allocationProgressBar.className = 'progress-bar-fill fill-warning';
                dom.cargoValidationMessage.innerHTML = `<span class="error-feedback">⚠️ ${diff.toLocaleString()} boxes remaining to allocate to reach ${target.toLocaleString()}.</span>`;
            } else {
                dom.cargoBalanceBadge.textContent = `+${Math.abs(diff).toLocaleString()} Over`;
                dom.cargoBalanceBadge.className = 'badge badge-danger';
                dom.allocationProgressBar.className = 'progress-bar-fill fill-danger';
                dom.cargoValidationMessage.innerHTML = `<span class="error-feedback">⚠️ Total exceeds ship capacity by ${Math.abs(diff).toLocaleString()} boxes!</span>`;
            }
        }
    }

    // --- Route Calculation (Call Backend) ---
    async function calculateRoute() {
        dom.calculateRouteBtn.disabled = true;
        dom.calculateRouteBtn.innerHTML = '<span class="btn-icon">⏳</span> Computing Shortest Sea Route...';

        const payload = {
            startPortId: state.startPortId,
            destinationPortIds: Array.from(state.selectedDestIds),
            shipCapacity: state.shipCapacity,
            cargoAllocations: state.cargoAllocations
        };

        try {
            const res = await fetch('/api/route/calculate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const data = await res.json();

            if (!data.success) {
                const errorMsg = data.errors && data.errors.length > 0 ? data.errors.join('<br>') : data.message;
                showToast(errorMsg, 'error');
                dom.voyageLiveText.textContent = '⚠️ ' + (data.message || 'Route calculation error');
                return;
            }

            state.currentRouteData = data;
            displayCalculatedRoute(data);
            showToast('Shortest sea route calculated! ' + data.route.totalDistanceNm.toFixed(0) + ' NM total.', 'success');

        } catch (err) {
            console.error(err);
            showToast('Network error calculating route: ' + err.message, 'error');
        } finally {
            dom.calculateRouteBtn.disabled = false;
            dom.calculateRouteBtn.innerHTML = '<span class="btn-icon">🧭</span> Calculate Shortest Sea Route';
        }
    }

    // --- Display Route on Map and Schedule Table ---
    function displayCalculatedRoute(data) {
        dom.voyageExecutionCard.style.display = 'block';
        
        // Populate Telemetry Metrics
        dom.metricDistance.textContent = `${data.route.totalDistanceNm.toFixed(0).toLocaleString()} NM`;
        dom.metricDistanceKm.textContent = `${data.route.totalDistanceKm.toFixed(0).toLocaleString()} km`;
        dom.metricStops.textContent = data.stops.length;
        dom.metricDelivered.textContent = '0';
        dom.metricRemaining.textContent = state.shipCapacity.toLocaleString();
        dom.deliveryProgressBar.style.width = '0%';
        dom.deliveryPercentageText.textContent = '0%';
        
        dom.voyageLiveText.textContent = `⚓ MV Pirates Voyager ready at ${data.stops[0].name}. Embark on voyage to begin delivery.`;

        // Render Stop-by-Stop Table
        dom.stopsTableBody.innerHTML = '';
        data.stops.forEach((stop, index) => {
            const tr = document.createElement('tr');
            tr.id = `stopRow_${index}`;
            const isOrigin = index === 0;

            tr.innerHTML = `
                <td><strong>${stop.stopNumber}</strong></td>
                <td class="port-name-cell">
                    <div>${stop.name}</div>
                    <small style="color:var(--text-muted);">${stop.country} ${isOrigin ? '(Departure)' : ''}</small>
                </td>
                <td style="color:var(--accent-gold); font-weight:700;">${stop.boxesToUnload.toLocaleString()}</td>
                <td style="color:#fff;">${stop.boxesRemainingAfterUnload.toLocaleString()}</td>
                <td><span class="badge ${isOrigin ? 'badge-primary' : 'badge-info'}" id="stopBadge_${index}">${stop.status}</span></td>
            `;
            dom.stopsTableBody.appendChild(tr);
        });

        // Map Graphics: Clear old route
        clearMapRoute();

        // Extract GeoJSON coordinates for the ship animation and the leg-by-leg route drawing
        const geojson = data.route.geoJson;
        const lineFeature = geojson.features.find(f => f.geometry.type === 'LineString');

        if (lineFeature) {
            // GeoJSON coordinates are [lon, lat]; Leaflet expects [lat, lon]
            state.animation.routeCoordinates = lineFeature.geometry.coordinates.map(c => [c[1], c[0]]);

            // Find where each stop sits along the path, split the path into legs,
            // and draw every leg in its own colour with direction arrows
            findStopCoordinateIndices(data.stops);
            buildLegs(data);
            renderLegsPanel();
            drawLegs();

            // Fit map bounds to show the entire voyage
            fitAllLegs();
        }

        // Draw Port Markers with Numbered Pins
        data.stops.forEach((stop, index) => {
            const isOrigin = index === 0;
            const isFinal = index === data.stops.length - 1;
            const pinIcon = createNumberedPin(stop.stopNumber, isOrigin, isFinal);

            const marker = L.marker([stop.latitude, stop.longitude], { icon: pinIcon }).addTo(map);
            
            marker.bindPopup(`
                <div class="popup-title">Stop ${stop.stopNumber}: ${stop.name}</div>
                <div class="popup-meta">
                    Country: <strong>${stop.country}</strong><br>
                    Boxes to Unload: <strong>${stop.boxesToUnload.toLocaleString()}</strong><br>
                    Remaining After Stop: <strong>${stop.boxesRemainingAfterUnload.toLocaleString()}</strong><br>
                    Status: <strong style="color:var(--accent-ocean);">${stop.status}</strong>
                </div>
            `);

            state.animation.portMarkers.push(marker);
        });

        // Place MV Pirates Voyager ship marker at Start Port
        if (state.animation.routeCoordinates.length > 0) {
            const startCoord = state.animation.routeCoordinates[0];
            const initialHeading = calculateBearing(
                startCoord,
                state.animation.routeCoordinates[Math.min(5, state.animation.routeCoordinates.length - 1)]
            );

            state.animation.shipMarker = L.marker(startCoord, {
                icon: createShipIcon(initialHeading),
                zIndexOffset: 1000
            }).addTo(map);
            
            state.animation.shipMarker.bindPopup(`
                <div class="popup-title">🚢 MV Pirates Voyager</div>
                <div class="popup-meta">
                    Flagship Cargo Vessel<br>
                    Current Port: <strong>${data.stops[0].name}</strong><br>
                    Current Cargo: <strong>${state.shipCapacity.toLocaleString()} boxes</strong>
                </div>
            `);
        }

        // Scroll sidebar down so user immediately sees the metrics & Start button
        dom.voyageExecutionCard.scrollIntoView({ behavior: 'smooth' });
    }

    function clearMapRoute() {
        if (state.animation.animFrameId) {
            cancelAnimationFrame(state.animation.animFrameId);
            state.animation.animFrameId = null;
        }
        state.animation.isRunning = false;
        state.animation.isPaused = false;
        state.animation.currentStep = 0;

        // Remove all leg lines, arrows and labels
        state.legLayer.clearLayers();
        state.legs = [];
        state.selectedLeg = null;
        state.animation.shipIndex = 0;
        if (state.animation.shipMarker) {
            map.removeLayer(state.animation.shipMarker);
            state.animation.shipMarker = null;
        }
        state.animation.portMarkers.forEach(m => map.removeLayer(m));
        state.animation.portMarkers = [];
    }

    // Match each stop port to its coordinate index in the polyline.
    // The stops are searched in visiting order (each one after the previous), so a route that
    // passes the same waters twice (for example down and back up the Red Sea) is split correctly.
    function findStopCoordinateIndices(stops) {
        const coords = state.animation.routeCoordinates;
        const last = coords.length - 1;
        const indices = [0];
        let previous = 0;

        for (let s = 1; s < stops.length; s++) {
            if (s === stops.length - 1) {
                indices.push(last);   // the final stop is the end of the path
                break;
            }

            // Leave room for the stops that still come after this one
            const highest = Math.min(last, Math.max(previous + 1, last - (stops.length - 1 - s)));
            let bestIdx = Math.min(previous + 1, last);
            let bestDist = Infinity;
            for (let i = previous + 1; i <= highest; i++) {
                const dist = Math.hypot(coords[i][0] - stops[s].latitude, coords[i][1] - stops[s].longitude);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
                if (dist < 1e-6) break;   // exact hit: the ship is at the port
            }
            indices.push(bestIdx);
            previous = bestIdx;
        }

        state.animation.stopIndices = indices;
    }

    // --- Ship Voyage Simulation Animation Engine ---
    function startVoyage() {
        if (!state.currentRouteData || state.animation.routeCoordinates.length === 0) return;

        if (state.animation.isPaused) {
            // Resume
            state.animation.isPaused = false;
            state.animation.isRunning = true;
            dom.startVoyageBtn.style.display = 'none';
            dom.pauseVoyageBtn.style.display = 'inline-flex';
            dom.voyageStatusBadge.textContent = 'Underway';
            dom.voyageStatusBadge.className = 'badge badge-success';
            animateStep();
            return;
        }

        // Fresh Start: Start Port Unload First (User Requirement #1)
        state.animation.isRunning = true;
        state.animation.isPaused = false;
        state.animation.currentStep = 0;
        state.animation.shipIndex = 0;
        updateSailedProgress();
        state.animation.deliveredBoxes = 0;
        state.animation.remainingBoxes = state.shipCapacity;

        dom.startVoyageBtn.style.display = 'none';
        dom.pauseVoyageBtn.style.display = 'inline-flex';
        dom.voyageStatusBadge.textContent = 'Underway';
        dom.voyageStatusBadge.className = 'badge badge-success';

        // Check if start port unloads cargo
        const startStop = state.currentRouteData.stops[0];
        if (startStop.boxesToUnload > 0) {
            dom.voyageLiveText.textContent = `📦 Origin Unloading: Disembarking ${startStop.boxesToUnload.toLocaleString()} boxes at ${startStop.name}...`;
            updateStopTableRow(0, 'Unloading...', 'badge-warning');

            setTimeout(() => {
                state.animation.deliveredBoxes += startStop.boxesToUnload;
                state.animation.remainingBoxes -= startStop.boxesToUnload;
                updateTelemetryUI();
                updateStopTableRow(0, 'Delivered ✓', 'badge-success');
                showToast(`Unloaded ${startStop.boxesToUnload.toLocaleString()} boxes at origin (${startStop.name}).`, 'info');
                
                dom.voyageLiveText.textContent = `🌊 MV Pirates Voyager departed ${startStop.name}. Cruising toward Stop 2: ${state.currentRouteData.stops[1].name}...`;
                animateStep();
            }, 1000 / state.animation.speedMultiplier);
        } else {
            updateStopTableRow(0, 'Departed', 'badge-primary');
            dom.voyageLiveText.textContent = `🌊 MV Pirates Voyager departed ${startStop.name}. Cruising toward Stop 2: ${state.currentRouteData.stops[1].name}...`;
            animateStep();
        }
    }

    function pauseVoyage() {
        if (!state.animation.isRunning) return;
        state.animation.isPaused = true;
        state.animation.isRunning = false;
        if (state.animation.animFrameId) {
            cancelAnimationFrame(state.animation.animFrameId);
            state.animation.animFrameId = null;
        }
        dom.pauseVoyageBtn.style.display = 'none';
        dom.startVoyageBtn.style.display = 'inline-flex';
        dom.startVoyageBtn.innerHTML = '<span>▶</span> Resume';
        dom.voyageStatusBadge.textContent = 'Paused';
        dom.voyageStatusBadge.className = 'badge badge-warning';
        dom.voyageLiveText.textContent = '⏸ Voyage paused by captain.';
    }

    function resetVoyage() {
        if (state.animation.animFrameId) {
            cancelAnimationFrame(state.animation.animFrameId);
            state.animation.animFrameId = null;
        }
        state.animation.isRunning = false;
        state.animation.isPaused = false;
        state.animation.currentStep = 0;
        state.animation.deliveredBoxes = 0;
        state.animation.remainingBoxes = state.shipCapacity;

        dom.pauseVoyageBtn.style.display = 'none';
        dom.startVoyageBtn.style.display = 'inline-flex';
        dom.startVoyageBtn.innerHTML = '<span>▶</span> Start Voyage';
        dom.voyageStatusBadge.textContent = 'Ready';
        dom.voyageStatusBadge.className = 'badge badge-success';

        if (state.currentRouteData) {
            displayCalculatedRoute(state.currentRouteData);
        }
    }

    // Step-by-step ship movement along the maritime coordinates
    function animateStep() {
        if (!state.animation.isRunning || state.animation.isPaused) return;

        const coords = state.animation.routeCoordinates;
        const currentIdx = state.animation.currentStep;

        if (currentIdx >= coords.length - 1) {
            // Reached final stop
            completeVoyage();
            return;
        }

        // Advance ship marker position
        const currentPos = coords[currentIdx];
        const nextPos = coords[Math.min(currentIdx + 1, coords.length - 1)];

        // Calculate heading to rotate ship icon
        const heading = calculateBearing(currentPos, nextPos);
        state.animation.shipMarker.setLatLng(currentPos);
        state.animation.shipMarker.setIcon(createShipIcon(heading));

        // Fill in the solid "already sailed" line behind the ship
        state.animation.shipIndex = currentIdx;
        updateSailedProgress();

        // Check if current coordinate reached any intermediate destination stop
        const stops = state.currentRouteData.stops;
        const reachedStopIndex = state.animation.stopIndices.findIndex((stopCoordIdx, sIdx) => {
            return sIdx > 0 && Math.abs(stopCoordIdx - currentIdx) <= 2;
        });

        if (reachedStopIndex > 0) {
            // Prevent re-triggering for same stop
            const stop = stops[reachedStopIndex];
            const badge = document.getElementById(`stopBadge_${reachedStopIndex}`);
            
            if (badge && !badge.textContent.includes('Delivered') && !badge.textContent.includes('Unloading')) {
                // Pause at port for cargo unloading
                state.animation.isRunning = false;
                dom.voyageLiveText.textContent = `⚓ Arrived at Stop ${stop.stopNumber}: ${stop.name}. Unloading ${stop.boxesToUnload.toLocaleString()} boxes...`;
                updateStopTableRow(reachedStopIndex, 'Unloading...', 'badge-warning');

                // Animate port marker pulse
                const marker = state.animation.portMarkers[reachedStopIndex];
                if (marker) marker.openPopup();

                setTimeout(() => {
                    state.animation.deliveredBoxes += stop.boxesToUnload;
                    state.animation.remainingBoxes -= stop.boxesToUnload;
                    updateTelemetryUI();
                    updateStopTableRow(reachedStopIndex, 'Delivered ✓', 'badge-success');
                    showToast(`Unloaded ${stop.boxesToUnload.toLocaleString()} boxes at ${stop.name}!`, 'success');

                    // If not the final stop, announce heading to next stop
                    if (reachedStopIndex < stops.length - 1) {
                        const nextStop = stops[reachedStopIndex + 1];
                        dom.voyageLiveText.textContent = `🌊 Departing ${stop.name}. Sailing towards Stop ${nextStop.stopNumber}: ${nextStop.name}...`;
                    }

                    // Resume sailing
                    state.animation.isRunning = true;
                    // Jump slightly past to avoid double trigger
                    state.animation.currentStep = state.animation.stopIndices[reachedStopIndex] + 3;
                    animateStep();
                }, 1400 / state.animation.speedMultiplier);

                return;
            }
        }

        // Determine step stride based on speed
        const stride = Math.max(1, Math.round(1 * state.animation.speedMultiplier));
        state.animation.currentStep += stride;

        // Schedule next animation frame
        state.animation.animFrameId = requestAnimationFrame(animateStep);
    }

    function completeVoyage() {
        state.animation.isRunning = false;

        // Put the ship on the last point of the path and mark every leg as sailed
        const routeCoords = state.animation.routeCoordinates;
        if (routeCoords.length > 0) {
            state.animation.shipIndex = routeCoords.length - 1;
            if (state.animation.shipMarker) {
                state.animation.shipMarker.setLatLng(routeCoords[routeCoords.length - 1]);
            }
            updateSailedProgress();
        }
        const stops = state.currentRouteData.stops;
        const finalStopIdx = stops.length - 1;
        const finalStop = stops[finalStopIdx];

        // Ensure final stop is marked delivered
        updateStopTableRow(finalStopIdx, 'Delivered ✓', 'badge-success');
        state.animation.deliveredBoxes = state.shipCapacity;
        state.animation.remainingBoxes = 0;
        updateTelemetryUI();

        dom.pauseVoyageBtn.style.display = 'none';
        dom.startVoyageBtn.style.display = 'inline-flex';
        dom.startVoyageBtn.innerHTML = '<span>↺</span> Voyage Finished';
        dom.voyageStatusBadge.textContent = 'Completed';
        dom.voyageStatusBadge.className = 'badge badge-success';

        dom.voyageLiveText.textContent = `🎉 Voyage Completed! All ${state.shipCapacity.toLocaleString()} boxes safely delivered at ${finalStop.name} by MV Pirates Voyager.`;
        showToast('Voyage Completed! All cargo successfully delivered.', 'success');
    }

    function updateTelemetryUI() {
        dom.metricDelivered.textContent = state.animation.deliveredBoxes.toLocaleString();
        dom.metricRemaining.textContent = Math.max(0, state.animation.remainingBoxes).toLocaleString();
        
        const pct = Math.min(100, Math.round((state.animation.deliveredBoxes / state.shipCapacity) * 100));
        dom.deliveryProgressBar.style.width = `${pct}%`;
        dom.deliveryPercentageText.textContent = `${pct}%`;
    }

    function updateStopTableRow(index, statusText, badgeClass) {
        const row = document.getElementById(`stopRow_${index}`);
        const badge = document.getElementById(`stopBadge_${index}`);
        if (badge) {
            badge.textContent = statusText;
            badge.className = `badge ${badgeClass}`;
        }
        if (row) {
            if (statusText.includes('Unloading')) {
                row.className = 'active-voyage-row';
            } else if (statusText.includes('Delivered')) {
                row.className = 'delivered-row';
            }
        }
    }

    // Great circle forward azimuth / bearing calculation between two [lat, lon] points
    function calculateBearing(pos1, pos2) {
        const lat1 = pos1[0] * Math.PI / 180;
        const lon1 = pos1[1] * Math.PI / 180;
        const lat2 = pos2[0] * Math.PI / 180;
        const lon2 = pos2[1] * Math.PI / 180;

        const dLon = lon2 - lon1;
        const y = Math.sin(dLon) * Math.cos(lat2);
        const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        const brng = Math.atan2(y, x) * 180 / Math.PI;
        return (brng + 360) % 360;
    }

    // --- Custom Port Modal Dialog Handlers ---
    function openCustomPortModal(lat = '', lon = '') {
        dom.customPortModal.style.display = 'flex';
        dom.customPortModalError.style.display = 'none';
        dom.customPortName.value = '';
        dom.customCountry.value = '';
        dom.customLatitude.value = lat;
        dom.customLongitude.value = lon;
        dom.customPortName.focus();
    }

    function closeCustomPortModal() {
        dom.customPortModal.style.display = 'none';
    }

    async function handleSaveCustomPort(e) {
        e.preventDefault();
        dom.customPortModalError.style.display = 'none';

        const name = dom.customPortName.value.trim();
        const country = dom.customCountry.value.trim();
        const latitude = parseFloat(dom.customLatitude.value);
        const longitude = parseFloat(dom.customLongitude.value);

        if (!name) {
            showModalError('Please enter a port name.');
            return;
        }
        if (isNaN(latitude) || latitude < -90 || latitude > 90) {
            showModalError('Latitude must be between -90 and 90.');
            return;
        }
        if (isNaN(longitude) || longitude < -180 || longitude > 180) {
            showModalError('Longitude must be between -180 and 180.');
            return;
        }

        const newPort = {
            name: name,
            country: country || 'Custom',
            latitude: latitude,
            longitude: longitude
        };

        try {
            const res = await fetch('/api/ports', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(newPort)
            });
            const data = await res.json();

            if (!data.success) {
                showModalError(data.error || 'Failed to save port');
                return;
            }

            const savedPort = data.port;
            state.allPorts.push(savedPort);
            renderPortSelectors();
            closeCustomPortModal();

            // Center map on new port
            map.flyTo([savedPort.latitude, savedPort.longitude], 6);
            showToast(`Custom port '${savedPort.name}' registered!`, 'success');

        } catch (err) {
            showModalError('Error saving custom port: ' + err.message);
        }
    }

    function showModalError(msg) {
        dom.customPortModalError.textContent = msg;
        dom.customPortModalError.style.display = 'block';
    }

    // --- Toast Notifications Helper ---
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = message;
        dom.toastContainer.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(30px)';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    // --- Event Listeners Setup ---
    dom.startPortSelect.addEventListener('change', (e) => {
        state.startPortId = e.target.value;
        // If start port was previously selected in destinations, remove it
        state.selectedDestIds.delete(state.startPortId);
        onDestinationsChanged();
    });

    dom.portSearchInput.addEventListener('input', (e) => {
        const groups = {};
        state.allPorts.forEach(p => {
            if (!groups[p.country]) groups[p.country] = [];
            groups[p.country].push(p);
        });
        renderDestinationsList(groups, Object.keys(groups).sort(), e.target.value);
    });

    // Preset Demo Button: Japan, Indonesia, Madagascar
    dom.presetDemoBtn.addEventListener('click', () => {
        state.selectedDestIds.clear();
        state.selectedDestIds.add('JP_TYO'); // Japan (Tokyo)
        state.selectedDestIds.add('ID_JKT'); // Indonesia (Jakarta)
        state.selectedDestIds.add('MG_TOA'); // Madagascar (Toamasina)
        
        // Ensure start port is India (Mumbai)
        state.startPortId = 'IN_BOM';
        dom.startPortSelect.value = 'IN_BOM';

        onDestinationsChanged();
        autoBalanceCargo();
        showToast('Demo Preset selected: Japan, Indonesia, Madagascar.', 'info');
    });

    dom.clearDestsBtn.addEventListener('click', () => {
        state.selectedDestIds.clear();
        onDestinationsChanged();
    });

    dom.shipCapacityInput.addEventListener('change', (e) => {
        const val = parseInt(e.target.value) || 24000;
        state.shipCapacity = Math.max(100, val);
        e.target.value = state.shipCapacity;
        updateCargoValidation();
    });

    dom.resetCapacityBtn.addEventListener('click', () => {
        state.shipCapacity = 24000;
        dom.shipCapacityInput.value = 24000;
        updateCargoValidation();
    });

    dom.autoBalanceBtn.addEventListener('click', autoBalanceCargo);
    dom.calculateRouteBtn.addEventListener('click', calculateRoute);

    dom.startVoyageBtn.addEventListener('click', startVoyage);
    dom.pauseVoyageBtn.addEventListener('click', pauseVoyage);
    dom.resetVoyageBtn.addEventListener('click', resetVoyage);

    // Speed buttons
    dom.speedButtons.forEach(btn => {
        btn.addEventListener('click', (e) => {
            dom.speedButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            state.animation.speedMultiplier = parseFloat(btn.getAttribute('data-speed')) || 1;
        });
    });

    // Route legs: "Show all legs" button
    dom.showAllLegsBtn.addEventListener('click', showAllLegs);

    // Map legend: open / close
    dom.legendToggleBtn.addEventListener('click', () => {
        const isOpen = dom.legendBody.style.display !== 'none';
        dom.legendBody.style.display = isOpen ? 'none' : 'block';
        dom.legendToggleBtn.setAttribute('aria-expanded', String(!isOpen));
        dom.legendChevron.innerHTML = isOpen ? '&#9650;' : '&#9660;';
    });

    // Custom Port Modal Events
    dom.openCustomPortModalBtn.addEventListener('click', () => openCustomPortModal());
    dom.closeCustomPortModalBtn.addEventListener('click', closeCustomPortModal);
    dom.cancelCustomPortBtn.addEventListener('click', closeCustomPortModal);
    dom.customPortForm.addEventListener('submit', handleSaveCustomPort);

    // Initial Load
    fetchPorts();
});
