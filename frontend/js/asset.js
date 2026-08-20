// FR2, FR3 - asset detail: historical chart + record investment form. No arrow functions (cohort syntax rule).

var params = new URLSearchParams(window.location.search);
var ticker = params.get("ticker");
var assetType = params.get("assetType");
var name = params.get("name");

document.getElementById("assetTitle").textContent = ticker + " - " + name + " (" + assetType + ")";

// default date field to today, user can change it to backdate (FR3)
document.getElementById("dateAcquired").value = new Date().toISOString().split("T")[0];

async function loadHistory(range) {
    var errorEl = document.getElementById("historyError");
    errorEl.textContent = "";
    try {
        var series = await apiGet("/assets/" + ticker + "/history?range=" + range);

        if (series.length > 0) {
            var latest = series[series.length - 1];
            document.getElementById("currentPrice").textContent =
                "Current price: £" + Number(latest.price).toFixed(2) + " (as of " + latest.date + ")";
        }

        var canvas = document.getElementById("priceChart");
        drawLineChart(canvas, series);
    } catch (e) {
        errorEl.textContent = "Failed to load history: " + e.message;
    }
}

async function recordInvestment() {
    var quantity = document.getElementById("quantity").value;
    var dateAcquired = document.getElementById("dateAcquired").value;
    var errorEl = document.getElementById("recordError");
    var successEl = document.getElementById("recordSuccess");
    errorEl.textContent = "";
    successEl.textContent = "";

    if (!quantity || Number(quantity) <= 0) {
        errorEl.textContent = "Enter a valid quantity.";
        return;
    }
    if (!dateAcquired) {
        errorEl.textContent = "Choose an acquisition date.";
        return;
    }

    try {
        await apiPost("/holdings", {
            ticker: ticker,
            assetType: assetType,
            name: name,
            quantity: quantity,
            dateAcquired: dateAcquired
        });
        successEl.textContent = "Investment recorded. View it on the Portfolio page.";
    } catch (e) {
        errorEl.textContent = "Failed to record investment: " + e.message;
    }
}

// load a sensible default view on page load
loadHistory("1m");
