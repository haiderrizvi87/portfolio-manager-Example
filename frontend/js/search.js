// FR1 - search-as-you-type against Yahoo, via the backend. No arrow functions (cohort syntax rule).

async function doSearch() {
    var query = document.getElementById("searchInput").value.trim();
    var errorEl = document.getElementById("searchError");
    var resultsEl = document.getElementById("results");
    errorEl.textContent = "";
    resultsEl.style.display = "none";

    if (!query) {
        errorEl.textContent = "Enter a ticker or name to search.";
        return;
    }

    try {
        var results = await apiGet("/assets/search?q=" + encodeURIComponent(query));
        if (results.length === 0) {
            errorEl.textContent = "No matches found.";
            return;
        }

        var html = "";
        for (var i = 0; i < results.length; i = i + 1) {
            var r = results[i];
            var safeName = r.name.replace(/'/g, "\\'");
            html = html + "<div class=\"search-result\" onclick=\"selectAsset('" + r.ticker + "', '" + r.assetType + "', '" + safeName + "')\">";
            html = html + "<strong>" + r.ticker + "</strong> - " + r.name + " <em>(" + r.assetType + ")</em>";
            html = html + "</div>";
        }
        resultsEl.innerHTML = html;
        resultsEl.style.display = "block";
    } catch (e) {
        errorEl.textContent = "Search failed: " + e.message;
    }
}

function selectAsset(ticker, assetType, name) {
    var params = new URLSearchParams({ ticker: ticker, assetType: assetType, name: name });
    window.location.href = "asset.html?" + params.toString();
}

function handleSearchKeydown(event) {
    if (event.key === "Enter") {
        doSearch();
    }
}

document.getElementById("searchInput").addEventListener("keydown", handleSearchKeydown);
