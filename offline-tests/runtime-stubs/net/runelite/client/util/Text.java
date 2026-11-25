package net.runelite.client.util;public class Text {public static String removeTags(String s){return s==null?null:s.replaceAll("<[^>]*>","");}}
