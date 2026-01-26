// Using React from global scope
const { useState, useEffect, createElement: h } = React;
const { createRoot } = ReactDOM;

// Simple Trading Journal - Standalone Version
function TradingJournal() {
  const [darkMode, setDarkMode] = useState(true);
  const [trades, setTrades] = useState([]);
  
  const colors = {
    bg: darkMode ? '#0A0E0F' : '#F8FAF9',
    surface: darkMode ? '#131819' : '#FFFFFF',
    surfaceVariant: darkMode ? '#1C2224' : '#F1F4F3',
    primary: darkMode ? '#00E676' : '#00C853',
    onSurface: darkMode ? '#E8EAED' : '#1A1C1E',
    outline: darkMode ? '#2D3438' : '#E0E3E1',
    profit: darkMode ? '#00E676' : '#00C853',
    loss: darkMode ? '#FF5252' : '#D32F2F',
  };
  
  useEffect(() => {
    const data = window.storage.get('trades');
    if (data?.value) {
      setTrades(JSON.parse(data.value));
    } else {
      const mockTrades = [{
        id: Date.now(),
        symbol: 'BTC/USDT',
        side: 'LONG',
        netPnL: 1250.50,
        returnPct: 5.2,
        timestamp: new Date().toISOString()
      }];
      setTrades(mockTrades);
      window.storage.set('trades', JSON.stringify(mockTrades));
    }
  }, []);
  
  const stats = {
    totalPnL: trades.reduce((sum, t) => sum + (t.netPnL ||  0), 0),
    totalTrades: trades.length
  };
  
  return h('div', { 
    style: { 
      minHeight: '100vh',
      background: colors.bg,
      color: colors.onSurface,
      padding: '40px',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    }
  },
    h('div', { style: { maxWidth: '1200px', margin: '0 auto' }},
      h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }},
        h('h1', { style: { fontSize: '32px', fontWeight: '800' }}, 'TradeFlow'),
        h('button', {
          onClick: () => setDarkMode(!darkMode),
          style: {
            background: colors.surfaceVariant,
            border: 'none',
            borderRadius: '10px',
            padding: '12px 20px',
            cursor: 'pointer',
            fontSize: '16px'
          }
        }, darkMode ? '☀️ Light' : '🌙 Dark')
      ),
      h('div', { style: {
        background: colors.surface,
        borderRadius: '20px',
        padding: '32px',
        marginBottom: '24px',
        border: `1px solid ${colors.outline}`
      }},
        h('h2', { style: { marginBottom: '20px' }}, 'Performance'),
        h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px' }},
          h('div', null,
            h('div', { style: { fontSize: '14px', opacity: 0.7, marginBottom: '8px' }}, 'Total P&L'),
            h('div', { 
              style: { fontSize: '28px', fontWeight: '800', color: stats.totalPnL >= 0 ? colors.profit : colors.loss }
            }, `${stats.totalPnL >= 0 ? '+' : ''}$${stats.totalPnL.toFixed(2)}`)
          ),
          h('div', null,
            h('div', { style: { fontSize: '14px', opacity: 0.7, marginBottom: '8px' }}, 'Total Trades'),
            h('div', { style: { fontSize: '28px', fontWeight: '800' }}, stats.totalTrades)
          )
        )
      ),
      h('div', { style: {
        background: colors.surface,
        borderRadius: '20px',
        padding: '32px',
        border: `1px solid ${colors.outline}`
      }},
        h('h2', { style: { marginBottom: '20px' }}, 'Recent Trades'),
        trades.length === 0 ? 
          h('p', { style: { opacity: 0.6, textAlign: 'center', padding: '40px' }}, 'No trades yet. Add your first trade to get started!') :
          trades.map(trade => 
            h('div', { 
              key: trade.id,
              style: {
                padding: '20px',
                marginBottom: '12px',
                background: colors.surfaceVariant,
                borderRadius: '12px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
              }
            },
              h('div', null,
                h('div', { style: { fontWeight: '700', fontSize: '16px', marginBottom: '4px' }}, trade.symbol),
                h('div', { style: {
                  display: 'inline-block',
                  padding: '4px 12px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: '700',
                  background: trade.side === 'LONG' ? `${colors.profit}25` : `${colors.loss}25`,
                  color: trade.side === 'LONG' ? colors.profit : colors.loss
                }}, trade.side)
              ),
              h('div', { style: { textAlign: 'right' }},
                h('div', { 
                  style: { fontSize: '20px', fontWeight: '800', color: trade.netPnL >= 0 ? colors.profit : colors.loss }
                }, `${trade.netPnL >= 0 ? '+' : ''}$${trade.netPnL.toFixed(2)}`),
                h('div', { style: { fontSize: '14px', opacity: 0.7 }}, 
                  `${trade.returnPct >= 0 ? '+' : ''}${trade.returnPct.toFixed(2)}%`)
              )
            )
          )
      ),
      h('div', { style: {
        marginTop: '40px',
        padding: '24px',
        background: colors.surfaceVariant,
        borderRadius: '12px',
        textAlign: 'center'
      }},
        h('p', { style: { opacity: 0.8, marginBottom: '8px' }}, '✨ Complete UI loading...'),
        h('p', { style: { fontSize: '14px', opacity: 0.6 }}, 
          'The full trading journal with charts, analytics, and trade management is being initialized.')
      )
    )
  );
}

// Render
const root = createRoot(document.getElementById('root'));
root.render(h(TradingJournal));
