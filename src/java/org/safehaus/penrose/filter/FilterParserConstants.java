/* Generated By:JavaCC: Do not edit this line. FilterParserConstants.java */
/**
 * Copyright (c) 2000-2005, Identyx Corporation.
 * All rights reserved.
 */
package org.safehaus.penrose.filter;

public interface FilterParserConstants {

  int EOF = 0;
  int AND = 4;
  int OR = 5;
  int NOT = 6;
  int LPAREN = 7;
  int RPAREN = 8;
  int ANY = 9;
  int COLON = 10;
  int SPACE = 11;
  int ITEM = 12;

  int DEFAULT = 0;

  String[] tokenImage = {
    "<EOF>",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"&\"",
    "\"|\"",
    "\"!\"",
    "\"(\"",
    "\")\"",
    "\"*\"",
    "\":\"",
    "\" \"",
    "<ITEM>",
  };

}
