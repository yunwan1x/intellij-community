/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class GlobalSearchScope extends SearchScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.GlobalSearchScope");

  public abstract boolean contains(VirtualFile file);

  /**
   * @param file1
   * @param file2
   * @return a positive integer (+1), if file1 is located in the classpath before file2,
   *         a negative integer (-1), if file1 is located in the classpath after file2
   *         zero - otherwise or when the file are not comparable.
   */
  public abstract int compare(VirtualFile file1, VirtualFile file2);

  // optimization methods:

  public abstract boolean isSearchInModuleContent(@NotNull Module aModule);

  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return isSearchInModuleContent(aModule);
  }

  public abstract boolean isSearchInLibraries();

  private static final String ALL_SCOPE_NAME = PsiBundle.message("psi.search.scope.project.and.libraries");
  @NotNull
  public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (ALL_SCOPE_NAME.equals(scope.getDisplayName())) return this;
    return intersection(this, scope);
  }

  private static GlobalSearchScope intersection(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2) {
    return new IntersectionScope(scope1, scope2, null);
  }

  private static GlobalSearchScope union(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2) {
    return new UnionScope(scope1, scope2, null);
  }
  public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (ALL_SCOPE_NAME.equals(scope.getDisplayName())) return scope;
    return union(this, scope);
  }

  public static GlobalSearchScope allScope(@NotNull Project project) {
    return ProjectScope.getAllScope(project);
  }

  public static GlobalSearchScope projectScope(@NotNull Project project) {
    return ProjectScope.getProjectScope(project);
  }

  public static GlobalSearchScope projectProductionScope(@NotNull Project project, final boolean includeNonJavaFiles) {
    return new IntersectionScope(projectScope(project),
                                 new ProductionScopeFilter(project, includeNonJavaFiles),
                                 PsiBundle.message("psi.search.scope.production.files"));
  }

  public static GlobalSearchScope projectTestScope(@NotNull Project project, final boolean includeNonJavaFiles) {
    return new IntersectionScope(projectScope(project),
                                 new TestScopeFilter(project, includeNonJavaFiles),
                                 PsiBundle.message("psi.search.scope.test.files"));
  }

  public static GlobalSearchScope filterScope(@NotNull Project project, @NotNull NamedScope set) {
    return new FilterScopeAdapter(project, set);
  }

  public static GlobalSearchScope notScope(@NotNull final GlobalSearchScope scope) {
    return new GlobalSearchScope() {
      public boolean contains(final VirtualFile file) {
        return !scope.contains(file);
      }

      public int compare(final VirtualFile file1, final VirtualFile file2) {
        return scope.compare(file1, file2);
      }

      public boolean isSearchInModuleContent(@NotNull final Module aModule) {
        return scope.isSearchInLibraries();
      }

      public boolean isSearchInLibraries() {
        return scope.isSearchInLibraries();
      }
    };
  }

  /**
   * Returns module scope including sources and tests, excluding libraries and dependencies.
   *
   * @param module the module to get the scope.
   * @return scope including sources and tests, excluding libraries and dependencies.
   */
  public static GlobalSearchScope moduleScope(@NotNull Module module) {
    return module.getModuleScope();
  }

  /**
   * Returns module scope including sources, tests, and libraries, excluding dependencies.
   *
   * @param module the module to get the scope.
   * @return scope including sources, tests, and libraries, excluding dependencies.
   */
  public static GlobalSearchScope moduleWithLibrariesScope(@NotNull Module module) {
    return module.getModuleWithLibrariesScope();
  }

  /**
   * Returns module scope including sources, tests, and dependencies, excluding libraries.
   *
   * @param module the module to get the scope.
   * @return scope including sources, tests, and dependencies, excluding libraries.
   */
  public static GlobalSearchScope moduleWithDependenciesScope(@NotNull Module module) {
    return module.getModuleWithDependenciesScope();
  }

  public static GlobalSearchScope moduleRuntimeScope(@NotNull Module module, final boolean includeTests) {
    return module.getModuleRuntimeScope(includeTests);
  }

  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(Module module) {
    return moduleWithDependenciesAndLibrariesScope(module, true);
  }

  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module, boolean includeTests) {
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  public static GlobalSearchScope moduleWithDependentsScope(@NotNull Module module) {
    return module.getModuleWithDependentsScope();
  }

  public static GlobalSearchScope moduleTestsWithDependentsScope(@NotNull Module module) {
    return module.getModuleWithDependentsScope();
  }

  public static GlobalSearchScope packageScope(@NotNull PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, true);
  }

  public static GlobalSearchScope directoryScope(@NotNull PsiDirectory directory, final boolean withSubdirectories) {
    return new DirectoryScope(directory, withSubdirectories);
  }

  public static GlobalSearchScope packageScopeWithoutLibraries(@NotNull PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, false);
  }

  public static SearchScope fileScope(@NotNull PsiFile psiFile) {
    return new FileScope(psiFile);
  }

  private static class IntersectionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;
    private final String myDisplayName;

    public IntersectionScope(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2, String displayName) {
      myScope1 = scope1;
      myScope2 = scope2;
      myDisplayName = displayName;
    }

    public String getDisplayName() {
      if (myDisplayName == null) {
        return PsiBundle.message("psi.search.scope.intersection", myScope1.getDisplayName(), myScope2.getDisplayName());
      }
      return myDisplayName;
    }

    public boolean contains(VirtualFile file) {
      return myScope1.contains(file) && myScope2.contains(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      int res1 = myScope1.compare(file1, file2);
      int res2 = myScope2.compare(file1, file2);

      if (res1 == 0) return res2;
      if (res2 == 0) return res1;

      res1 /= Math.abs(res1);
      res2 /= Math.abs(res2);
      if (res1 == res2) return res1;

      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return myScope1.isSearchInModuleContent(aModule) && myScope2.isSearchInModuleContent(aModule);
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return myScope1.isSearchInModuleContent(aModule, testSources) && myScope2.isSearchInModuleContent(aModule, testSources);
    }

    public boolean isSearchInLibraries() {
      return myScope1.isSearchInLibraries() && myScope2.isSearchInLibraries();
    }
  }
  private static class UnionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;
    private final String myDisplayName;

    public UnionScope(GlobalSearchScope scope1, GlobalSearchScope scope2, String displayName) {
      myScope1 = scope1;
      myScope2 = scope2;
      myDisplayName = displayName;
      LOG.assertTrue(myScope1 != null);
      LOG.assertTrue(myScope2 != null);
    }

    public String getDisplayName() {
      if (myDisplayName == null) {
        return PsiBundle.message("psi.search.scope.union", myScope1.getDisplayName(), myScope2.getDisplayName());
      }
      return myDisplayName;
    }

    public boolean contains(VirtualFile file) {
      return myScope1.contains(file) || myScope2.contains(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      int res1 = myScope1.contains(file1) && myScope1.contains(file2) ? myScope1.compare(file1, file2) : 0;
      int res2 = myScope2.contains(file1) && myScope2.contains(file2) ? myScope2.compare(file1, file2) : 0;

      if (res1 == 0) return res2;
      if (res2 == 0) return res1;

      res1 /= Math.abs(res1);
      res2 /= Math.abs(res2);
      if (res1 == res2) return res1;

      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return myScope1.isSearchInModuleContent(aModule) || myScope2.isSearchInModuleContent(aModule);
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return myScope1.isSearchInModuleContent(aModule, testSources) || myScope2.isSearchInModuleContent(aModule, testSources);
    }

    public boolean isSearchInLibraries() {
      return myScope1.isSearchInLibraries() || myScope2.isSearchInLibraries();
    }
  }

  private static class ProductionScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;
    private final boolean myIncludeNonJavaFiles;

    public ProductionScopeFilter(Project project, boolean includeNonJavaFiles) {
      myIncludeNonJavaFiles = includeNonJavaFiles;
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean contains(VirtualFile file) {
      if (!myFileIndex.isJavaSourceFile(file)) return myIncludeNonJavaFiles;
      return myFileIndex.isInSourceContent(file) && !myFileIndex.isInTestSourceContent(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return !testSources;
    }

    public boolean isSearchInLibraries() {
      return false;
    }
  }

  private static class TestScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;
    private final boolean myIncludeNonJavaFiles;

    public TestScopeFilter(Project project, boolean includeNonJavaFiles) {
      myIncludeNonJavaFiles = includeNonJavaFiles;
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    public boolean contains(VirtualFile file) {
      if (!myFileIndex.isJavaSourceFile(file)) return myIncludeNonJavaFiles;
      return myFileIndex.isInTestSourceContent(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return testSources;
    }

    public boolean isSearchInLibraries() {
      return false;
    }
  }

  private static class PackageScope extends GlobalSearchScope {
    private final Collection<VirtualFile> myDirs;
    private final PsiPackage myPackage;
    private final boolean myIncludeSubpackages;
    private final boolean myIncludeLibraries;

    public PackageScope(@NotNull PsiPackage aPackage, boolean includeSubpackages, final boolean includeLibraries) {
      myPackage = aPackage;
      myIncludeSubpackages = includeSubpackages;

      Project project = myPackage.getProject();
      FileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      myDirs = fileIndex.getDirsByPackageName(myPackage.getQualifiedName(), true).findAll();
      myIncludeLibraries = includeLibraries;
    }

    public boolean contains(VirtualFile file) {
      for (VirtualFile scopeDir : myDirs) {
        boolean inDir = myIncludeSubpackages
                        ? VfsUtil.isAncestor(scopeDir, file, false)
                        : Comparing.equal(file.getParent(), scopeDir);
        if (inDir) return true;
      }
      return false;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "package scope: " + myPackage +
             ", includeSubpackages = " + myIncludeSubpackages;
    }
  }
  private static class DirectoryScope extends GlobalSearchScope {
    private final VirtualFile myDirectory;
    private final boolean myWithSubdirectories;

    public DirectoryScope(PsiDirectory directory, final boolean withSubdirectories) {
      myWithSubdirectories = withSubdirectories;
      myDirectory = directory.getVirtualFile();
    }

    public boolean contains(VirtualFile file) {
      if (myWithSubdirectories) {
        return VfsUtil.isAncestor(myDirectory, file, false);
      }
      else {
        return myDirectory.equals(file.getParent());
      }
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    public boolean isSearchInLibraries() {
      return false;
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "directory scope: " + myDirectory + "; withSubdirs:"+myWithSubdirectories;
    }
  }

  private static class FileScope extends GlobalSearchScope {
    private final VirtualFile myVirtualFile;
    private final Module myModule;

    public FileScope(PsiFile psiFile) {
      myVirtualFile = psiFile.getVirtualFile();
      Project project = psiFile.getProject();
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      myModule = myVirtualFile != null ? fileIndex.getModuleForFile(myVirtualFile) : null;
    }

    public boolean contains(VirtualFile file) {
      return Comparing.equal(myVirtualFile, file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return aModule == myModule;
    }

    public boolean isSearchInLibraries() {
      return myModule == null;
    }
  }

  private static class FilterScopeAdapter extends GlobalSearchScope {
    private final NamedScope mySet;
    private final PsiManager myManager;
    private final Project myProject;

    public FilterScopeAdapter(Project project, NamedScope set) {
      mySet = set;
      myProject = project;
      myManager = PsiManager.getInstance(myProject);
    }

    public boolean contains(VirtualFile file) {
      PsiFile psiFile = myManager.findFile(file);
      if (psiFile == null) return false;
      NamedScopesHolder holder = NamedScopeManager.getInstance(myProject);
      final PackageSet packageSet = mySet.getValue();
      return packageSet != null && packageSet.contains(psiFile, holder);
    }

    public String getDisplayName() {
      return mySet.getName();
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;

    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true; //TODO (optimization?)
    }

    public boolean isSearchInLibraries() {
      return true; //TODO (optimization?)
    }
  }

  public static GlobalSearchScope getScopeRestrictedByFileTypes (final GlobalSearchScope scope, final FileType... fileTypes) {
    LOG.assertTrue(fileTypes.length > 0);
    return new FileTypeRestrictionScope(scope, fileTypes);
  }

  private static class FileTypeRestrictionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope;
    private final FileType[] myFileTypes;

    public FileTypeRestrictionScope(final GlobalSearchScope scope, final FileType[] fileTypes) {
      myFileTypes = fileTypes;
      myScope = scope;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return myScope.compare(file1, file2);
    }

    public boolean contains(VirtualFile file) {
      if (!myScope.contains(file)) return false;

      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
      for (FileType otherFileType : myFileTypes) {
        if (fileType.equals(otherFileType)) return true;
      }

      return false;
    }

    public boolean isSearchInLibraries() {
      return myScope.isSearchInLibraries();
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return myScope.isSearchInModuleContent(aModule);
    }
  }

  private static class EmptyScope extends GlobalSearchScope {
    public boolean contains(VirtualFile file) {
      return false;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return false;
    }

    @NotNull
    public GlobalSearchScope intersectWith(@NotNull final GlobalSearchScope scope) {
      return this;
    }

    public GlobalSearchScope uniteWith(@NotNull final GlobalSearchScope scope) {
      return scope;
    }
  }

  public static final GlobalSearchScope EMPTY_SCOPE = new EmptyScope();
}
