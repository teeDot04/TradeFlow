import unittest
import pandas as pd
import numpy as np
import logging
from main import TradeFlowAgent

# Disable logging for clean test output
logging.disable(logging.CRITICAL)

class TestTradeFlowResearch(unittest.TestCase):
    """
    Scientific Verification Unit Tests
    Validates that the Code strictly follows the Math in 'technical_whitepaper.md' (Version 4.0).
    """

    def setUp(self):
        # Initialize Agent (Exchange ID irrelevant for pure logic tests)
        self.agent = TradeFlowAgent(exchange_id='okx')

    def create_mock_row(self, price, adx, atr, s1, s2, s3, supertrend, st_dir, rsi=40, close_prev_supertrend=False):
        """Generates a synthetic market snapshot (Vector)"""
        ts = pd.Timestamp.now()
        
        # Current Row
        # Ensure Green Candle for entry triggers (Code requires Close > Open)
        row = pd.Series({
            'ts': ts,
            'open': price - 0.5, # Slightly lower to make it Green
            'high': price + 10,
            'low': price - 10,
            'close': price,
            'adx': adx,
            'atr': atr,
            'S1': s1,
            'S2': s2,
            'S3': s3,
            'supertrend': supertrend,
            'supertrend_dir': st_dir, # 1=Green, -1=Red
            'rsi': rsi
        })

        # Previous Row (needed for Trend Flip detection)
        # If close_prev_supertrend is True, we simulate a 'Flip' (Prev was Red/Below)
        prev_st_dir = -1 if st_dir == 1 else 1
        
        prev_row = pd.Series({
            'ts': ts,
            'close': price - 50 if st_dir == 1 else price + 50,
            'supertrend': supertrend, 
            'supertrend_dir': prev_st_dir 
        })
        
        return row, prev_row

    def test_theorem_1_regime_switch_range(self):
        """
        Theorem 1.1: If ADX < 25, System MUST use Mean Reversion Logic (Pivot Hunter).
        """
        print("\nTesting Theorem 1.1: Range Regime (ADX < 25)...")
        
        # Scenario: ADX = 15 (Range), Price = 99 (Below S1 which is 100), RSI = 30 (Oversold)
        # Expectation: SIGNAL_LONG_MEAN_REV
        
        row, prev = self.create_mock_row(
            price=99.5, # Slightly above "0.99 * S1" (99.0)
            adx=15.0,   # RANGE REGIME
            atr=10.0,
            s1=100.0,
            s2=98.0,
            s3=95.0,
            supertrend=110.0, # Bearish Supertrend (should be ignored in Pivot trade?)
            st_dir=-1
        )
        
        # We need to hack 'low' to trigger the "Touched S1" logic
        # Code: touched_s1 = row['low'] <= (row['S1'] * 1.005)
        row['low'] = 99.0 
        
        signal = self.agent.analyze_market_snapshot(row, prev, regime_thresh=25)
        
        self.assertIsNotNone(signal, "Agent failed to detect valid Pivot Bounce")
        self.assertEqual(signal['type'], 'SMART_HYBRID')
        self.assertFalse(signal['trailing'], "Range Trades must NOT trail")
        
        # MATH CHECK: SL should be Price - 1.0 * ATR
        expected_sl = 99.5 - (1.0 * 10.0) # 89.5
        self.assertEqual(signal['sl'], expected_sl, f"SL Math Error: Expected {expected_sl}, got {signal['sl']}")
        print(f"✅ PASS: Range Logic Active. SL formula verified: {signal['sl']}")

    def test_theorem_1_regime_switch_trend(self):
        """
        Theorem 1.2: If ADX >= 25, System MUST use Momentum Logic (Trend Surfer).
        """
        print("\nTesting Theorem 1.2: Trend Regime (ADX >= 25)...")
        
        # Scenario: ADX = 40 (Strong Trend), Price = 105 (Above SuperTrend 100)
        # Entry Condition: We need a Trigger. 
        # In main.py loop, 'at_support' takes precedence. 
        # If NOT at_support, it checks 'trend_flip'.
        
        row, prev = self.create_mock_row(
            price=105.0, 
            adx=40.0,   # TREND REGIME
            atr=5.0,
            s1=90.0,   # Far away
            s2=80.0,
            s3=70.0,
            supertrend=100.0, # Below Price (Bullish)
            st_dir=1 
        )
        
        # Mock a Trend Flip: Current Green (1), Prev Red (-1)
        prev['supertrend_dir'] = -1 
        
        signal = self.agent.analyze_market_snapshot(row, prev, regime_thresh=25)
        
        self.assertIsNotNone(signal, "Agent failed to detect Trend Flip")
        self.assertTrue(signal['trailing'], "Trend Trades MUST trail")
        self.assertIsNone(signal['tp2'], "Trend Trades MUST have infinite TP (None)")
        
        # MATH CHECK: SL should be SuperTrend Line
        self.assertEqual(signal['sl'], 100.0, f"Trend SL Error: Expected SuperTrend (100.0), got {signal['sl']}")
        print(f"✅ PASS: Trend Logic Active. Infinite TP verified.")

    def test_theorem_2_volatility_normalization(self):
        """
        Theorem 2: Stop Distance must adapt to Volatility (ATR).
        Test Asset A (Low Vol) vs Asset B (High Vol).
        """
        print("\nTesting Theorem 2: Volatility Normalization (ATR)...")
        
        # Asset A: Stablecoin (ATR = 1)
        row_a, prev_a = self.create_mock_row(100, 10, 1.0, 101, 99, 90, 110, -1)
        row_a['low'] = 100 # Trigger S1 (101)
        
        try:
             # Force logic entry
             sig_a = self.agent.analyze_market_snapshot(row_a, prev_a, 25)
        except:
             sig_a = None # Should not fail
             
        # Asset B: Meme Coin (ATR = 20)
        row_b, prev_b = self.create_mock_row(100, 10, 20.0, 101, 99, 90, 110, -1)
        row_b['low'] = 100
        
        sig_b = self.agent.analyze_market_snapshot(row_b, prev_b, 25)
        
        # Calculate Risks
        risk_a = sig_a['price'] - sig_a['sl'] # Should be 1.0
        risk_b = sig_b['price'] - sig_b['sl'] # Should be 20.0
        
        self.assertAlmostEqual(risk_a, 1.0)
        self.assertAlmostEqual(risk_b, 20.0)
        
        print(f"✅ PASS: Risk scaled dynamically. Asset A Risk: ${risk_a}, Asset B Risk: ${risk_b}")

if __name__ == '__main__':
    unittest.main()
