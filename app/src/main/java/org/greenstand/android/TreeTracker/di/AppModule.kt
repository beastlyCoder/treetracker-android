package org.greenstand.android.TreeTracker.di

import org.greenstand.android.TreeTracker.managers.PlanterManager
import org.greenstand.android.TreeTracker.managers.TreeManager
import org.greenstand.android.TreeTracker.managers.UserManager
import org.greenstand.android.TreeTracker.viewmodels.DataViewModel
import org.greenstand.android.TreeTracker.viewmodels.LoginViewModel
import org.greenstand.android.TreeTracker.viewmodels.TermsPolicyViewModel
import org.greenstand.android.TreeTracker.viewmodels.TreeHeightViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    viewModel { LoginViewModel(get()) }

    viewModel { TermsPolicyViewModel(get()) }

    viewModel { TreeHeightViewModel(get()) }

    viewModel { DataViewModel(get(), get(), get(), get()) }

    single { TreeManager(get()) }

    single { UserManager(get()) }

    single { PlanterManager(get(), get()) }
}