package examples;

/* --------------------------------------------------------------------------
 * File: CutStock.java
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
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

import ilog.concert.*;
import ilog.cplex.*;

class CutStock {
    static double RC_EPS = 1.0e-6;
    
    static double rollWidth;
    static double[] size;
    static double[] amount;

    static void readData(String fileName) throws IOException, InputDataReader.InputDataReaderException {
        InputDataReader reader = new InputDataReader(fileName);

        rollWidth = reader.readDouble();
        size = reader.readDoubleArray();
        amount = reader.readDoubleArray();
    }

    /**
     * Description .<br>
     * 
     * @param rmlpSolver
     * @param cut
     * @param fill
     * @throws IloException
     */
    static void report1(IloCplex rmlpSolver, IloNumVarArray cut, IloRange[] fill) throws IloException {
        System.out.println();
        System.out.println("Using " + rmlpSolver.getObjValue() + " rolls");

        System.out.println();
        for (int j = 0; j < cut.getSize(); j++) {
            System.out.println("  Cut" + j + " = " + rmlpSolver.getValue(cut.getCutNum(j)));
        }
        System.out.println();

        for (int i = 0; i < fill.length; i++) {
            System.out.println("  Fill" + i + " = " + rmlpSolver.getDual(fill[i]));
        }
        System.out.println();
    }

    static void report2(IloCplex patSolver, IloNumVar[] times) throws IloException {
        System.out.println();
        System.out.println("Reduced cost is " + patSolver.getObjValue());

        System.out.println();
        if (patSolver.getObjValue() <= -RC_EPS) {
            for (int i = 0; i < times.length; i++) {
                System.out.println("  Use" + i + " = " + patSolver.getValue(times[i]));
            }
            System.out.println();
        }
    }

    static void report3(IloCplex rmlpSolver, IloNumVarArray cut) throws IloException {
        System.out.println();
        System.out.println("Best integer solution uses " + rmlpSolver.getObjValue() + " rolls");
        System.out.println();
        for (int j = 0; j < cut.getSize(); j++) {
            System.out.println("  Cut" + j + " = " + rmlpSolver.getValue(cut.getCutNum(j)));
        }
    }

    /**
     * 各切割方案使用的次数 .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    static class IloNumVarArray {
        int num = 0;
        IloNumVar[] array = new IloNumVar[32];
        ArrayList<double[]> patterns = new ArrayList<double[]>();

        void add(IloNumVar ivar) {
            // resizing the array
            if (num >= array.length) {
                IloNumVar[] newArray = new IloNumVar[2 * array.length];
                System.arraycopy(array, 0, newArray, 0, num);
                array = newArray;
            }
            array[num++] = ivar;
        }
        
        void add(double[] pattern) {
            patterns.add(pattern);
        }

        IloNumVar getCutNum(int i) {
            return array[i];
        }
        
        double[] getPattern(int i) {
            return patterns.get(i);
        }

        int getSize() {
            return num;
        }
    }
    
    
    public static void main(String[] args) {
        String datafile = "./data/cutstock.dat";
        try {
            if (args.length > 0) {
                datafile = args[0];
            }
            readData(datafile);

            // RMLP（MLP）的求解器
            IloCplex rmlpSolver = new IloCplex();
            
            // RMLP Model
            IloObjective rollsUsed = rmlpSolver.addMinimize();
            IloRange[] fill = new IloRange[amount.length];
            for (int f = 0; f < amount.length; f++) {
                // MLP Model中的各类木材需求量约束
                fill[f] = rmlpSolver.addRange(amount[f], Double.MAX_VALUE);
            }
            
            // 存储列生成过程中的切割方案
            IloNumVarArray cutPattern = new IloNumVarArray();
            
            /*
             * RMLP Model
             * 每次添加新的列，包括目标函数的变化、约束条件的变化，这里先初始化：
             * 初始化列，得到nWdth列，每列对应一种切割方案——只产生一种长度木材的切割方案
             */
            int nWdth = size.length;
            for (int j = 0; j < nWdth; j++) {
                cutPattern.add(rmlpSolver.numVar(
                        rmlpSolver.column(rollsUsed, 1.0).and(rmlpSolver.column(fill[j], (int) (rollWidth / size[j]))),
                        0.0, Double.MAX_VALUE));
                
                double[] pattern = new double[nWdth];
                pattern[j] = (int) (rollWidth / size[j]);
                cutPattern.add(pattern);
            }
            
            // 设置求解参数 - 采用单纯形法
            rmlpSolver.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);

            // Pricing Model
            IloCplex patSolver = new IloCplex();
            IloObjective reducedCost = patSolver.addMinimize();
            // 添加变量，切割方案对应的每种长度的木材的份数
            IloNumVar[] times = patSolver.numVarArray(nWdth, 0., Double.MAX_VALUE, IloNumVarType.Int);
            // Pricing Problem的原料长度约束
            patSolver.addRange(-Double.MAX_VALUE, patSolver.scalProd(size, times), rollWidth);

            // 列生成过程核心步骤
            double[] newPatt = new double[nWdth];
            for (;;) {
                
                // Solve the RMLP to get the primal(upper bound) and dual solution
                rmlpSolver.solve();
                report1(rmlpSolver, cutPattern, fill);

                /// Solve the Pricing Problem，产生新的列
                double[] price = rmlpSolver.getDuals(fill);
                // Pricing Problem 的目标函数更新
                reducedCost.setExpr(patSolver.diff(1., patSolver.scalProd(times, price)));
                // 求解Pricing Problem
                patSolver.solve();
                report2(patSolver, times);
                
                // 若Pricing Problem的目标值大于0表示MLP问题求解完成，退出循环；否则生成新的列进行求解
                if (patSolver.getObjValue() > -RC_EPS) {
                    break;
                }
                
                newPatt = patSolver.getValues(times);

                // 存储切割方案
                cutPattern.add(newPatt);
                
                /*
                 * 依据新的切割方案更新RMLP问题，包括：
                 * 1.目标值
                 * 2.约束
                 * 3.变量
                 * 采用按column添加的方式
                 */
                IloColumn column = rmlpSolver.column(rollsUsed, 1.);
                for (int p = 0; p < newPatt.length; p++) {
                    // 更新约束
                    column = column.and(rmlpSolver.column(fill[p], newPatt[p]));
                }
                // 添加变量，并更新切割方案数组
                cutPattern.add(rmlpSolver.numVar(column, 0., Double.MAX_VALUE));
            }
            
            for (int i = 0; i < cutPattern.getSize(); i++) {
                // 将变量转化为int类型
                rmlpSolver.add(rmlpSolver.conversion(cutPattern.getCutNum(i), IloNumVarType.Int));
                
            }
            
            rmlpSolver.solve();
            rmlpSolver.exportModel("model2.lp");
            
            System.out.println("Solution status: " + rmlpSolver.getStatus());
            
            System.out.println("Cut plan: ");
            for (int i = 0; i < cutPattern.num; i++) {
                System.out.print("Cut" + i + " \n= " + rmlpSolver.getValue(cutPattern.getCutNum(i)));
                System.out.println("    Pattern" + " = " + Arrays.toString(cutPattern.getPattern(i)));
            }
            
            // 关闭求解器
            rmlpSolver.end();
            patSolver.end();
        } catch (IloException exc) {
            System.err.println("Concert exception '" + exc + "' caught");
        } catch (IOException exc) {
            System.err.println("Error reading file " + datafile + ": " + exc);
        } catch (InputDataReader.InputDataReaderException exc) {
            System.err.println(exc);
        }
    }
}

/*
 * Example Input file: 
 * 115
 * [25, 40, 50, 55, 70]
 * [50, 36, 24, 8, 30]
 */
