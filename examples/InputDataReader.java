package examples;
/* --------------------------------------------------------------------------
 * File: InputDataReader.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2001, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 * This is a helper class used by several examples to read input data files
 * containing arrays in the format [x1, x2, ..., x3].  Up to two-dimensional
 * arrays are supported.
 */

import java.io.*;

public class InputDataReader {
   public static class InputDataReaderException extends Exception {
      private static final long serialVersionUID = 1021L;
      InputDataReaderException(String file) {
         super("'" + file + "' contains bad data format");
      }
   }
   
   StreamTokenizer _tokenizer;
   Reader _reader;
   String _fileName;

   public InputDataReader(String fileName) throws IOException {
      _reader = new FileReader(fileName);
      _fileName = fileName;
    
      _tokenizer = new StreamTokenizer(_reader);
    
      // State the '"', '\'' as white spaces.
      _tokenizer.whitespaceChars('"', '"');
      _tokenizer.whitespaceChars('\'', '\'');
    
      // State the '[', ']' as normal characters.
      _tokenizer.ordinaryChar('[');
      _tokenizer.ordinaryChar(']');
      _tokenizer.ordinaryChar(',');
   }

   protected void finalize() throws Throwable {
      _reader.close();
   }

   double readDouble() throws InputDataReaderException,
                              IOException {
      int ntType = _tokenizer.nextToken();
      
      if ( ntType != StreamTokenizer.TT_NUMBER )
         throw new InputDataReaderException(_fileName);
      
      return _tokenizer.nval;
   }
     
   int readInt() throws InputDataReaderException,
                        IOException {
      int ntType = _tokenizer.nextToken();
    
      if ( ntType != StreamTokenizer.TT_NUMBER )
         throw new InputDataReaderException(_fileName);
      
      return (new Double(_tokenizer.nval)).intValue();
   }
   
   double[] readDoubleArray() throws InputDataReaderException,
                                     IOException {
      int ntType = _tokenizer.nextToken(); // Read the '['
      
      if ( ntType != '[' )
         throw new InputDataReaderException(_fileName);
      
      DoubleArray values = new DoubleArray();
      ntType = _tokenizer.nextToken();
      while (ntType == StreamTokenizer.TT_NUMBER) {
         values.add(_tokenizer.nval);
         ntType = _tokenizer.nextToken();
         
         if ( ntType == ',' ) {
            ntType = _tokenizer.nextToken();
         }
         else if ( ntType != ']' ) {
            throw new InputDataReaderException(_fileName);
         }
      }
      
      if ( ntType != ']' )
         throw new InputDataReaderException(_fileName);
    
      // Allocate and fill the array.
      double[] res = new double[values.getSize()];
      for (int i = 0; i < values.getSize(); i++) {
         res[i] = values.getElement(i);
      }
      
      return res;
   }

   double[][] readDoubleArrayArray() throws InputDataReaderException,
                                            IOException {
      int ntType = _tokenizer.nextToken(); // Read the '['
      
      if ( ntType != '[' )
         throw new InputDataReaderException(_fileName);
      
      DoubleArrayArray values = new DoubleArrayArray();
      ntType = _tokenizer.nextToken();
      
      while (ntType == '[') {
         _tokenizer.pushBack();
         
         values.add(readDoubleArray());
         
         ntType = _tokenizer.nextToken();
         if      ( ntType == ',' ) {
           ntType = _tokenizer.nextToken();
         }
         else if ( ntType != ']' ) {
           throw new InputDataReaderException(_fileName);
         }
      }
    
      if ( ntType != ']' )
         throw new InputDataReaderException(_fileName);
    
      // Allocate and fill the array.
      double[][] res = new double[values.getSize()][];
      for (int i = 0; i < values.getSize(); i++) { 
         res[i] = new double[values.getSize(i)];
         for (int j = 0; j < values.getSize(i); j++) { 
            res[i][j] = values.getElement(i,j);
         }
      }
      return res;
   }

   int[] readIntArray() throws InputDataReaderException,
                               IOException {
      int ntType = _tokenizer.nextToken(); // Read the '['
      
      if ( ntType != '[' )
         throw new InputDataReaderException(_fileName);
      
      IntArray values = new IntArray();
      ntType = _tokenizer.nextToken();
      while (ntType == StreamTokenizer.TT_NUMBER) {
         values.add(_tokenizer.nval);
         ntType = _tokenizer.nextToken();
         
         if      ( ntType == ',' ) {
            ntType = _tokenizer.nextToken();
         }
         else if ( ntType != ']' ) {
            throw new InputDataReaderException(_fileName);
         }
      }
      
      if ( ntType != ']' )
         throw new InputDataReaderException(_fileName);

      // Allocate and fill the array.
      int[] res = new int[values.getSize()];
      for (int i = 0; i < values.getSize(); i++) {
         res[i] = values.getElement(i);
      }
      return res;
   }

   int[][] readIntArrayArray() throws InputDataReaderException,
                                      IOException {
      int ntType = _tokenizer.nextToken(); // Read the '['
      
      if ( ntType != '[' )
         throw new InputDataReaderException(_fileName);
      
      IntArrayArray values = new IntArrayArray();
      ntType = _tokenizer.nextToken();
      
      while (ntType == '[') {
         _tokenizer.pushBack();
         
         values.add(readIntArray());
         
         ntType = _tokenizer.nextToken();
         if      ( ntType == ',' ) {
            ntType = _tokenizer.nextToken();
         }
         else if ( ntType != ']' ) {
            throw new InputDataReaderException(_fileName);
         }
      }
    
      if ( ntType != ']' )
         throw new InputDataReaderException(_fileName);
    
      // Allocate and fill the array.
      int[][] res = new int[values.getSize()][];
      for (int i = 0; i < values.getSize(); i++) {
         res[i] = new int[values.getSize(i)];
         for (int j = 0; j < values.getSize(i); j++) {
            res[i][j] = values.getElement(i,j);
         }
      }    
      return res;
   }

   private class DoubleArray {
      int      _num   = 0;
      double[] _array = new double[32];

      final void add(double dval) {
         if ( _num >= _array.length ) {
            double[] array = new double[2 * _array.length];
            System.arraycopy(_array, 0, array, 0, _num);
            _array = array;
         }
         _array[_num++] = dval;
      }

      final double getElement(int i) { return _array[i]; }
      final int    getSize()         { return _num; }
   }

   private class DoubleArrayArray {
      int        _num   = 0;
      double[][] _array = new double[32][];

      final void add(double[] dray) {

         if ( _num >= _array.length ) {
            double[][] array = new double[2 * _array.length][];
            for (int i = 0; i < _num; i++) {
               array[i] = _array[i];
            }
            _array = array;
         }
         _array[_num] = new double[dray.length];
         System.arraycopy(dray, 0, _array[_num], 0, dray.length);
         _num++;
      }

      final double getElement(int i, int j) { return _array[i][j]; }
      final int    getSize()                { return _num; }
      final int    getSize(int i)           { return _array[i].length; }
   }


   private class IntArray {
      int   _num   = 0;
      int[] _array = new int[32];

      final void add(double ival) {
         if ( _num >= _array.length ) {
            int[] array = new int[2 * _array.length];
            System.arraycopy(_array, 0, array, 0, _num);
            _array = array;
         }
         _array[_num++] = (int)Math.round(ival);
      }

      final int getElement(int i) { return _array[i]; }
      final int getSize()         { return _num; }
   }

   private class IntArrayArray {
      int     _num   = 0;
      int[][] _array = new int[32][];

      final void add(int[] iray) {

         if ( _num >= _array.length ) {
            int[][] array = new int[2 * _array.length][];
            for (int i = 0; i < _num; i++) {
               array[i] = _array[i];
            }
            _array = array;
         }
         _array[_num] = new int[iray.length];
         System.arraycopy(iray, 0, _array[_num], 0, iray.length);
         _num++;
      }

      final int getElement(int i, int j) { return _array[i][j]; }
      final int getSize()                { return _num; }
      final int getSize(int i)           { return _array[i].length; }
   }
}
