"""python3 -m unittest tools/bulletin/test_bulletin.py"""
import unittest
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("bulletin", Path(__file__).with_name("bulletin.py"))
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)

ROLLING_AUTO_VTT = """WEBVTT
Kind: captions
Language: en

00:00:02.619 --> 00:00:07.195 align:start position:0%
[music]

00:00:07.195 --> 00:00:09.000 align:start position:0%
Hello<00:00:07.500><c> everyone</c>

00:00:09.000 --> 00:00:09.010 align:start position:0%
Hello everyone

00:00:09.010 --> 00:00:12.000 align:start position:0%
Hello everyone
today we talk about AI

00:00:45.000 --> 00:00:47.000 align:start position:0%
second part &amp; more
"""


class VttTests(unittest.TestCase):
    def test_rolling_duplicates_removed_and_timestamped(self):
        text = b.vtt_to_text(ROLLING_AUTO_VTT)
        self.assertEqual(text, "[0:07] Hello everyone today we talk about AI\n[0:45] second part & more")

    def test_clock(self):
        self.assertEqual(b.clock(252), "4:12")
        self.assertEqual(b.clock(3725), "1:02:05")


class SubtitlePickTests(unittest.TestCase):
    def test_prefers_manual_in_video_language(self):
        meta = {"language": "tr", "subtitles": {"en": [], "tr": []}, "automatic_captions": {"tr-orig": []}}
        self.assertEqual(b.pick_subtitle(meta), ("tr", False))

    def test_falls_back_to_original_auto_captions(self):
        meta = {"language": "en-US", "subtitles": {}, "automatic_captions": {"en-orig": [], "en": [], "tr": []}}
        self.assertEqual(b.pick_subtitle(meta), ("en-orig", True))

    def test_none_when_no_captions(self):
        self.assertEqual(b.pick_subtitle({"language": "de"}), (None, None))


if __name__ == "__main__":
    unittest.main()
