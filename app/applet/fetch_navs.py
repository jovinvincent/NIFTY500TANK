import urllib.request
import json
import time
from datetime import datetime

schemes = {
    'quant_bfsi_fund_direct_plan_growth_option.json': 151791,
    'quant_infrastructure_fund_direct_plan_growth_option.json': 120833,
    'quant_healthcare_fund_direct_plan_growth_option.json': 151853,
    'quant_mid_cap_fund_direct_plan_growth_option.json': 120841,
    'quant_quantamental_fund_direct_plan_growth_option.json': 148925,
    'quant_value_fund_direct_plan_growth_option.json': 149335,
    'quant_esg_integration_strategy_fund_direct_plan_growth_option.json': 148564,
    'quant_arbitrage_fund_direct_plan_growth_option.json': 153426,
    'quant_equity_savings_fund_direct_plan_growth_option.json': 153705
}

for filename, code in schemes.items():
    success = False
    for attempt in range(3):
        try:
            print(f"[{code}] Fetching for {filename} (attempt {attempt+1})...", flush=True)
            url = f"https://api.mfapi.in/mf/{code}"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                records = data.get('data', [])
                if not records:
                    print(f"  Empty records for {code}", flush=True)
                    continue
                out_dict = {}
                for r in records:
                    d_str = r.get('date') # dd-MM-yyyy
                    val = float(r.get('nav', 0.0))
                    if d_str and val > 0.0:
                        dt = datetime.strptime(d_str, '%d-%m-%Y')
                        iso_date = dt.strftime('%Y-%m-%d')
                        nice_date = dt.strftime('%d-%b-%Y')
                        out_dict[iso_date] = val
                        out_dict[nice_date] = val
                with open(f"app/src/main/assets/{filename}", "w") as f:
                    json.dump(out_dict, f, indent=2)
                print(f"  Successfully wrote {len(records)} records for {filename} (Latest: {records[0]})", flush=True)
                success = True
                break
        except Exception as e:
            print(f"  Error on {code}: {e}", flush=True)
            time.sleep(2)
    if not success:
        print(f"FAILED TO FETCH {filename}", flush=True)
