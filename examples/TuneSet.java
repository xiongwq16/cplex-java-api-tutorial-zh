package examples;
/* --------------------------------------------------------------------------
 * File: TuneSet.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2007, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 * TuneSet.java - Tune parameters for a set of problems
 *
 * To run this example, command line arguments are required.
 * i.e.,   java TuneSet [options] file1 file2 ... filen
 * where
 *     each filei is the name of a file, with .mps, .lp, or
 *        .sav extension
 *     options are described in usage().
 * Example:
 *     java TuneSet  mexample.mps
 */

import ilog.concert.*;
import ilog.cplex.*;

import java.util.Vector;

public class TuneSet {
   static void usage() {
      System.out.println("usage:  TuneSet [options] file1 file2 ... filen");
      System.out.println("   where");
      System.out.println
          ("      filei is a file with extension MPS, SAV, or LP");
      System.out.println("      and options are:");
      System.out.println("         -a for average measure");
      System.out.println("         -m for minmax measure");
      System.out.println
          ("         -f <file> where file is a fixed parameter file");
      System.out.println
          ("         -o <file> where file is the tuned parameter file");
   }

   public static void main(String[] args) {
      if ( args.length < 1 ) {
         usage();
         return;
      }
      try {
         IloCplex cplex = new IloCplex();

         String         fixedfile = null;
         String         tunedfile = null;
         int            tunemeasure = 0;
         boolean        mset = false;
         Vector<String> filenames = new Vector<String>();

         for (int i = 0; i < args.length; ++i) {
            if ( args[i].charAt(0) != '-' ) {
               filenames.add(args[i]);
               continue;
            }
            switch ( args[i].charAt(1) ) {
            case 'a':
               tunemeasure = 1;
               mset = true;
               break;
            case 'm':
               tunemeasure = 2;
               mset = true;
               break;
            case 'f':
               fixedfile = args[++i];
               break;
            case 'o':
               tunedfile = args[++i];
               break;
            }
         }

         System.out.println("Problem set:");
         for (String name : filenames) {
            System.out.println("  " + name);
         }

         if ( mset )
            cplex.setParam(IloCplex.Param.Tune.Measure, tunemeasure);

         IloCplex.ParameterSet paramset = null;

         if ( fixedfile != null ) {
            cplex.readParam(fixedfile);
            paramset = cplex.getParameterSet();
            cplex.setDefaults();
         }

         int tunestat = cplex.tuneParam(filenames.toArray(new String[0]),
                                        paramset);

         if      ( tunestat == IloCplex.TuningStatus.Complete)
            System.out.println("Tuning complete.");
         else if ( tunestat == IloCplex.TuningStatus.Abort)
            System.out.println("Tuning abort.");
         else if ( tunestat == IloCplex.TuningStatus.TimeLim)
            System.out.println("Tuning time limit.");
         else
            System.out.println("Tuning status unknown.");

         if ( tunedfile != null ) {
            cplex.writeParam(tunedfile);
            System.out.println("Tuned parameters written to file '" +
                               tunedfile + "'");
         }
         cplex.end();
      }
      catch (IloException e) {
         System.err.println("Concert exception caught: " + e);
      }
   }
}
