// Thin wrapper around Chart.js (MIT licensed, loaded locally from js/vendor/chart.umd.js -
// see the <script> tag in each HTML page - no CDN, works offline).
// Chart.js replaces the earlier hand-rolled canvas drawing: still raw JavaScript calling it,
// no React/Vue/Angular (cohort rule). No arrow functions in our own code (cohort syntax rule);
// Chart.js's own internals are a third-party library and are not held to that rule.
//
// Same public functions as before - drawLineChart(canvas, points) and
// drawBarChart(canvas, labels, values) - so asset.js, performance.js, and summary.js did not
// need to change at all; only this file's internals changed.

var chartInstances = {};

function destroyExistingChart(canvas) {
    if (!canvas.id) {
        canvas.id = "chart-" + Math.random().toString(36).slice(2);
    }
    if (chartInstances[canvas.id]) {
        chartInstances[canvas.id].destroy();
        delete chartInstances[canvas.id];
    }
    return canvas.id;
}

function showNoData(canvas) {
    var ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillText("No data available", canvas.width / 2 - 40, canvas.height / 2);
}

function formatTick(value) {
    return Number(value).toFixed(2);
}

function drawLineChart(canvas, points) {
    // points: [{date: "yyyy-MM-dd", price: number}, ...]
    var id = destroyExistingChart(canvas);

    if (!points || points.length === 0) {
        showNoData(canvas);
        return;
    }

    var labels = [];
    var prices = [];
    for (var i = 0; i < points.length; i = i + 1) {
        labels.push(points[i].date);
        prices.push(Number(points[i].price));
    }

    var ctx = canvas.getContext("2d");
    chartInstances[id] = new Chart(ctx, {
        type: "line",
        data: {
            labels: labels,
            datasets: [{
                label: "Price",
                data: prices,
                borderColor: "#002a5c",
                backgroundColor: "rgba(0, 42, 92, 0.08)",
                borderWidth: 2,
                pointRadius: 0,
                fill: true,
                tension: 0.15
            }]
        },
        options: {
            responsive: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                x: { ticks: { maxTicksLimit: 8 } },
                y: { ticks: { callback: formatTick } }
            }
        }
    });
}

function drawBarChart(canvas, labels, values) {
    // values: array of numbers, can be positive or negative (P/L) - FR7
    var id = destroyExistingChart(canvas);

    if (!values || values.length === 0) {
        showNoData(canvas);
        return;
    }

    var barColors = [];
    for (var i = 0; i < values.length; i = i + 1) {
        if (values[i] >= 0) {
            barColors.push("#0a7a2e");
        } else {
            barColors.push("#b3261e");
        }
    }

    var ctx = canvas.getContext("2d");
    chartInstances[id] = new Chart(ctx, {
        type: "bar",
        data: {
            labels: labels,
            datasets: [{
                label: "Profit / Loss",
                data: values,
                backgroundColor: barColors
            }]
        },
        options: {
            responsive: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: { ticks: { callback: formatTick } }
            }
        }
    });
}
