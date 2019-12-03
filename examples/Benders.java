package examples;

/* --------------------------------------------------------------------------
 * File: Benders.java
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
 * Read in a model from a file and solve it using Benders decomposition.
 *
 * If an annotation file is provided, use that annotation file.
 * Otherwise, auto-decompose the problem and dump the annotation
 * to the file 'benders.ann'.
 *
 * To run this example, command line arguments are required.
 * i.e.,   java Benders   filename   [annofile]
 * where
 *     filename is the name of the file, with .mps, .lp, or .sav extension
 *     annofile is an optional .ann file with model annotations
 *
 * Example:
 *     java Benders  UFL_25_35_1.mps UFL_25_35_1.ann
 */

import ilog.concert.*;
import ilog.cplex.*;

public class Benders {
    static void usage() {
        System.out.println("usage:  Benders filename [annofile]");
        System.out.println("   where filename is a file with extension ");
        System.out.println("      MPS, SAV, or LP (lower case is allowed)");
        System.out.println("   and annofile is an optional .ann file with model annotations");
        System.out.println("      If \"create\" is used, the annotation is computed.");
        System.out.println(" Exiting...");
    }

    public static void main(String[] args) {
        IloCplex cpx = null;
        boolean hasAnnoFile = false;

        // Check the arguments.
        int argsLength = args.length;
        if (argsLength == 2) {
            hasAnnoFile = true;
        } else if (argsLength != 1) {
            usage();
            return;
        }

        try {
            // Create the modeler/solver object.
            cpx = new IloCplex();

            // Read the problem file.
            cpx.importModel(args[0]);

            // If provided, read the annotation file.
            if (hasAnnoFile) {
                // Generate default annotations if annofile is "create".
                if (args[1].equals("create")) {
                    IloCplex.LongAnnotation benders = cpx.newLongAnnotation(IloCplex.CPX_BENDERS_ANNOTATION,
                            IloCplex.CPX_BENDERS_MASTERVALUE);

                    IloLPMatrix lp = (IloLPMatrix) cpx.LPMatrixIterator().next();
                    IloNumVar[] var = lp.getNumVars();
                    // 识别连续变量
                    for (IloNumVar v : var) {
                        if (v.getType() == IloNumVarType.Float) {
                            cpx.setAnnotation(benders, v, IloCplex.CPX_BENDERS_MASTERVALUE + 1);
                        }
                    }
                } else {
                    // Otherwise, read the annotation file.
                    cpx.readAnnotations(args[1]);
                }
            } else {
                // Set benders strategy to auto-generate a decomposition.
                cpx.setParam(IloCplex.Param.Benders.Strategy, IloCplex.BendersStrategy.Full);

                // Write out the auto-generated annotation.
                cpx.writeBendersAnnotation("benders.ann");
            }

            // Solve the problem using Benders' decomposition.
            if (!cpx.solve()) {
                throw new RuntimeException("Failed to optimize.");
            }

            final IloCplex.Status status = cpx.getStatus();
            final double bestObjValue = cpx.getBestObjValue();
            final double objValue = cpx.getObjValue();
            System.out.println("Solution status: " + status);
            System.out.println("Best bound:      " + bestObjValue);
            System.out.println("Best integer:    " + objValue);
        } catch (IloException e) {
            throw new RuntimeException("Concert exception caught", e);
        } finally {
            if (cpx != null)
                cpx.end();
        }
    }
}
