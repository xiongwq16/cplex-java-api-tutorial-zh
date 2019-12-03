package examples;
/* --------------------------------------------------------------------------
 * File: MIPex4.java
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
 * MIPex4.java - Reading in and optimizing a problem using a callback
 *               to log or interrupt or an IloCplex.Aborter to interrupt
 *
 * To run this example, command line arguments are required.
 * i.e.,   MIPex4   filename option
 *                     where option is one of
 *                        t to use the time-limit-gap callback
 *                        l to use the logging callback
 *                        a to use the aborter
 *
 * Example:
 *     java MIPex4  mexample.mps l
 */

import ilog.concert.*;
import ilog.cplex.*;


public class MIPex4 {
   static void usage() {
      System.out.println("usage:  MIPex4 <filename> <option>");
      System.out.println("         t  to use the time-limit-gap callback");
      System.out.println("         l  to use the logging callback");
      System.out.println("         a  to use the aborter");
   }


   // Spend at least timeLimit seconds on optimization, but once
   // this limit is reached, quit as soon as the solution is acceptable

   static class TimeLimitCallback extends IloCplex.MIPInfoCallback {
      IloCplex _cplex;
      boolean  _aborted;
      double   _timeLimit;
      double   _timeStart;
      double   _acceptableGap;

      TimeLimitCallback(IloCplex cplex, boolean aborted, double timeStart,
                        double timeLimit, double acceptableGap) {
         _cplex         = cplex;
         _aborted       = aborted;
         _timeStart     = timeStart;
         _timeLimit     = timeLimit;
         _acceptableGap = acceptableGap;
      }
      
      public void main() throws IloException {
         if ( !_aborted  &&  hasIncumbent() ) {
            double gap = 100.0 * getMIPRelativeGap(); 
            double timeUsed = _cplex.getCplexTime() - _timeStart;
            if ( timeUsed > _timeLimit && gap < _acceptableGap ) {
               System.out.println("");
               System.out.println("Good enough solution at "
                                  + timeUsed + " sec., gap = "
                                  + gap  + "%, quitting."); 
               _aborted = true;
               abort();
            }
         }
      }
   }

   // Log new incumbents if they are at better than the old by a
   // relative tolerance of 1e-5; also log progress info every
   // 100 nodes.

   static class LogCallback extends IloCplex.MIPInfoCallback {
      IloNumVar[] _var;
      long        _lastLog;
      double      _lastIncumbent;

      LogCallback(IloNumVar[] var, int lastLog, double lastIncumbent) {
         _var = var;
         _lastLog = lastLog;
         _lastIncumbent = lastIncumbent;
      }

      public void main() throws IloException {
         boolean newIncumbent = false;
         long    nodes        = getNnodes64();

         if ( hasIncumbent()  &&
              Math.abs(_lastIncumbent - getIncumbentObjValue())
                 > 1e-5*(1.0 + Math.abs(getIncumbentObjValue())) ) {
            _lastIncumbent = getIncumbentObjValue();
            newIncumbent = true;
         }
         if ( nodes >= _lastLog + 100  ||  newIncumbent ) {
            if ( !newIncumbent )  _lastLog = nodes;
            System.out.print("Nodes = " + nodes
                             + "(" +  getNremainingNodes64() + ")"
                             + "  Best objective = " + getBestObjValue());
            if ( hasIncumbent() ) {
               System.out.println ("  Incumbent objective = " +
                                   getIncumbentObjValue());
            }
            else {
               System.out.println("");
            }
         }

         if ( newIncumbent ) {
            System.out.println("New incumbent values: ");

            int n = _var.length;
            double[] x = getIncumbentValues(_var, 0, n);
            for (int j = 0; j < n; j++) {
               System.out.println("Variable " + j + ": Value = " + x[j]);
            }
         }
      }
   }

   public static void main(String[] args) {
      if ( args.length != 2 ) {
         usage();
         return;
      }
      try {
         boolean useLoggingCallback = false;
         boolean useTimeLimitCallback = false;
         boolean useAborter = false;

         IloCplex.Aborter myAborter;

         IloCplex cplex = new IloCplex();

         switch ( args[1].charAt(0) ) {
         case 't':
            useTimeLimitCallback = true;
            break;
         case 'l':
            useLoggingCallback = true;
            break;
         case 'a':
            useAborter = true;
            break;
         default:
            usage();
            return;
         }

         cplex.importModel(args[0]);
         IloLPMatrix lp = (IloLPMatrix)cplex.LPMatrixIterator().next();
         IloObjective obj = cplex.getObjective();

         if ( useLoggingCallback ) {
            // Set an overall node limit in case callback conditions
            // are not met.
            cplex.setParam(IloCplex.Param.MIP.Limits.Nodes, 5000);

            double lastObjVal =
               (obj.getSense() == IloObjectiveSense.Minimize ) ?
                                  Double.MAX_VALUE : -Double.MAX_VALUE;

            cplex.use(new LogCallback(lp.getNumVars(), -100000, lastObjVal));
            // Turn off CPLEX logging
            cplex.setParam(IloCplex.Param.MIP.Display, 0);
         }
         else if ( useTimeLimitCallback ) {
            cplex.use(new TimeLimitCallback(cplex, false, cplex.getCplexTime(), 1.0, 10.0));
         }
         else if ( useAborter ) {
            myAborter =  new IloCplex.Aborter();
            cplex.use(myAborter);
            // Typically, you would pass the Aborter object to
            // another thread or pass it to an interrupt handler,
            // and  monitor for some event to occur.  When it does,
            // call the Aborter's abort method.
            //
            // To illustrate its use without creating a thread or
            // an interrupt handler, abort immediately by calling
            // abort before the solve.
            //
            myAborter.abort();
         }

         cplex.solve();
         System.out.println("Solution status = " + cplex.getStatus());
         System.out.println("CPLEX status = " + cplex.getCplexStatus());

         cplex.end();
      }
      catch (IloException e) {
         System.err.println("Concert exception caught: " + e);
      }
   }
}
