package com.codingwithmitch.food2forkkmm.interactors.recipe_list

import com.codingwithmitch.food2forkkmm.datasource.cache.RecipeDatabase
import com.codingwithmitch.food2forkkmm.datasource.network.RecipeService
import com.codingwithmitch.food2forkkmm.datasource.network.model.RecipeDtoMapper
import com.codingwithmitch.food2forkkmm.domain.model.GenericMessageInfo
import com.codingwithmitch.food2forkkmm.domain.model.PositiveAction
import com.codingwithmitch.food2forkkmm.domain.model.Recipe
import com.codingwithmitch.food2forkkmm.domain.util.*
import com.codingwithmitch.food2forkkmm.presentation.recipe_list.RecipeListState.Companion.RECIPE_PAGINATION_PAGE_SIZE
import com.codingwithmitch.food2forkkmm.util.Logger
import com.codingwithmitch.shared.domain.util.MessageType
import com.codingwithmitch.shared.domain.util.UIComponentType
import com.example.kmmplayground.shared.datasource.cache.model.RecipeEntityMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class SearchRecipes(
    private val recipeService: RecipeService,
    private val recipeDatabase: RecipeDatabase,
    private val recipeEntityMapper: RecipeEntityMapper,
    private val dateUtil: DatetimeUtil,
){

    private val logger = Logger("SearchRecipes")

    fun execute(
        page: Int,
        query: String,
    ): CommonFlow<DataState<List<Recipe>>> = flow  {
        try{
            emit(DataState.loading())

            // just to show pagination, api is fast
            delay(500)

            // force error for testing
            if (query == "error") {
                throw Exception("Forcing an error... Search FAILED!")
            }

            val recipes = recipeService.search(
                page = page,
                query = query,
            )

            // insert into cache
            val queries = recipeDatabase.recipeDbQueries
            val entities = recipeEntityMapper.toEntityList(recipes)
            for(entity in entities){
                queries.insertRecipe(
                    id = entity.id,
                    title = entity.title,
                    publisher = entity.publisher,
                    featured_image = entity.featuredImage,
                    rating = entity.rating,
                    source_url = entity.sourceUrl,
                    ingredients = entity.ingredients,
                    date_added = entity.dateAdded,
                    date_updated = entity.dateUpdated
                )
            }

            // query the cache
            val offset = (page - 1) * RECIPE_PAGINATION_PAGE_SIZE
            val cacheResult = if (query.isBlank()) {
                queries.getAllRecipes(
                    pageSize = RECIPE_PAGINATION_PAGE_SIZE.toLong(),
                    offset = offset.toLong()
                )
            } else {
                queries.searchRecipes(
                    query = query,
                    pageSize = RECIPE_PAGINATION_PAGE_SIZE.toLong(),
                    offset = offset.toLong()
                )
            }.executeAsList()

            // emit List<Recipe> from cache
            // Must manually map this since Recipe_Entity object is generated by SQL Delight
            val list: ArrayList<Recipe> = ArrayList()
            for(entity in cacheResult){
                list.add(Recipe(
                    id = entity.id.toInt(),
                    title = entity.title,
                    publisher = entity.publisher,
                    featuredImage = entity.featured_image,
                    rating = entity.rating.toInt(),
                    sourceUrl = entity.source_url,
                    ingredients = recipeEntityMapper.convertIngredientsToList(entity.ingredients),
                    dateAdded = dateUtil.toLocalDate(entity.date_added),
                    dateUpdated = dateUtil.toLocalDate(entity.date_updated)
                ))
            }

            emit(DataState.data<List<Recipe>>(message = null, data = list))
        } catch (e: Exception) {
            emit(DataState.error<List<Recipe>>(
                message = GenericMessageInfo.Builder()
                    .id("SearchRecipes.Error")
                    .title("Error")
                    .uiComponentType(UIComponentType.Dialog)
                    .messageType(MessageType.Error)
                    .description(e.message?: "Unknown Error")
            ))
        }
    }.asCommonFlow()

}



