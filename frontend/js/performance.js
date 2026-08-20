// FR6 - per-asset performance: line chart + per-holding breakdown with delete (FR5). No arrow functions (cohort syntax rule).

var params = new URLSearchParams(window.location.search);
var assetId = params.get("assetId");
var ticker = params.get("ticker");

document.getElementById("pageTitle").textContent = ticker + " - Performance";

async function loadPerformance() {
    var chartError = document.getElementById("chartError");
    var loadError = document.getElementById("loadError");
    var table = document.getElementById("holdingsTable");
    var body = document.getElementById("holdingsBody");

    try {
        var breakdown = await apiGet("/holdings/" + assetId + "/performance");
        if (breakdown.length === 0) {
            body.innerHTML = "";
            table.style.display = "none";
            loadError.textContent = "No holdings found for this asset.";
            return;
        }

        var html = "";
        for (var i = 0; i < breakdown.length; i = i + 1) {
            var h = breakdown[i];
            var plClass = "negative";
            if (h.profitLoss >= 0) {
                plClass = "positive";
            }
            html = html + "<tr>";
            html = html + "<td>" + h.totalQuantity + "</td>";
            html = html + "<td>" + formatMoney(h.avgPricePaid) + "</td>";
            html = html + "<td>" + formatMoney(h.currentPrice) + "</td>";
            html = html + "<td>" + formatMoney(h.currentValue) + "</td>";
            html = html + "<td>" + h.dateAcquired + "</td>";
            html = html + "<td class=\"" + plClass + "\">" + formatMoney(h.profitLoss) + " (" + formatPercent(h.profitLossPercent) + ")</td>";
            html = html + "<td><button onclick=\"deleteHolding(" + h.holdingId + ")\">Delete</button></td>";
            html = html + "</tr>";
        }
        body.innerHTML = html;
        table.style.display = "table";

        // FR6 - chart uses the ticker's historical series (5y range as a simple superset covering
        // any acquisition date; for a tighter chart, compute the earliest dateAcquired above
        // and request that exact range from the backend instead)
        var series = await apiGet("/assets/" + ticker + "/history?range=5y");
        drawLineChart(document.getElementById("perfChart"), series);

    } catch (e) {
        loadError.textContent = "Failed to load: " + e.message;
        chartError.textContent = e.message;
    }
}

async function deleteHolding(holdingId) {
    if (!confirm("Remove this holding? This cannot be undone.")) {
        return;
    }
    try {
        await apiDelete("/holdings/" + holdingId);
        loadPerformance();
    } catch (e) {
        alert("Failed to delete: " + e.message);
    }
}

loadPerformance();
